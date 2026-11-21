package com.hbmfarview.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiDepthRange;
import com.seibel.distanthorizons.api.enums.config.EDhApiRenderingApi;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.math.DhApiMat4f;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Copies the depth of the Distant Horizons terrain into Minecraft's depth buffer before entities are drawn.
 * Distant Horizons only merges its color into the frame, so without this the mushroom cloud is drawn over far hills.
 * Both renderers share the camera rotation and differ only in the projection, so each pixel's depth is converted
 * from the Distant Horizons projection to the Minecraft projection. Only depth is written, colors stay unchanged.
 * Only pixels where Minecraft has drawn nothing yet are touched. Where its own chunks are drawn,
 * writing Distant Horizons depth would only cause z-fighting (for example on water).
 */
final class DhDepthPass {
    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 150
            uniform sampler2D DhDepth;
            uniform vec2 ScreenSize;
            uniform vec2 DhProj;
            uniform vec2 McProj;
            uniform float DhClearDepth;
            uniform int DhZeroToOne;
            uniform float Bias;
            uniform sampler2D McDepth;
            out vec4 color;

            void main() {
                vec2 uv = gl_FragCoord.xy / ScreenSize;
                if (texture(McDepth, uv).r < 0.99999) {
                    discard;
                }
                float d = texture(DhDepth, uv).r;
                if (d == DhClearDepth) {
                    discard;
                }
                float ndc = DhZeroToOne == 1 ? d : d * 2.0 - 1.0;
                float viewZ = -DhProj.y / (DhProj.x + ndc);
                if (!(viewZ < 0.0)) {
                    discard;
                }
                viewZ *= 1.0 + Bias;
                float depth = (McProj.x * viewZ + McProj.y) / -viewZ * 0.5 + 0.5;
                if (!(depth > 0.0 && depth < 1.0)) {
                    discard;
                }
                gl_FragDepth = depth;
                color = vec4(0.0);
            }
            """;

    private static final float BIAS = 0.002F;

    private static boolean bound;
    private static final long FRESH_NANOS = 100_000_000L;
    private static volatile boolean hasProjection;
    private static volatile long lastDhFrame;
    private static volatile float dhA;
    private static volatile float dhB;

    private static int program = -1;
    private static int vao;
    private static int uDepth;
    private static int uScreen;
    private static int uDhProj;
    private static int uMcProj;
    private static int uClear;
    private static int uZeroToOne;
    private static int uBias;
    private static int uMcDepth;
    private static int depthCopy;
    private static int copyWidth;
    private static int copyHeight;

    private DhDepthPass() {
    }

    static void bindEvents() {
        if (bound) {
            return;
        }
        bound = true;
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhApiBeforeRenderEvent() {
            @Override
            public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
                capture(event.value.dhProjectionMatrix);
            }
        });
    }

    /**
     * A perspective matrix maps view depth z to clip z = A * z + B and clip w = -z.
     * Depending on the storage order, B sits in m23 or m32; the other one holds -1.
     */
    private static void capture(DhApiMat4f m) {
        lastDhFrame = System.nanoTime();
        dhA = m.m22;
        dhB = Math.abs(m.m32 + 1.0F) < 1.0E-3F ? m.m23 : m.m32;
        hasProjection = true;
    }

    /**
     * Returns true when the depth was written this frame.
     */
    static boolean run(Matrix4f mcProjection) {
        if (!hasProjection || System.nanoTime() - lastDhFrame > FRESH_NANOS) {
            return false;
        }
        IDhApiRenderProxy proxy = DhApi.Delayed.renderProxy;
        if (proxy == null || proxy.getRenderingApi() != EDhApiRenderingApi.OPEN_GL) {
            return false;
        }
        DhApiResult<Integer> texture = proxy.getDhDepthTextureGlId();
        if (!texture.success || texture.payload == null || texture.payload <= 0) {
            return false;
        }
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != target.frameBufferId) {
            return false;
        }
        if (program == -1) {
            program = createProgram();
        }
        if (program == 0 || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != target.frameBufferId
                || !ensureDepthCopy(target.width, target.height)) {
            return false;
        }

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActive = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        int previousDepthTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(depthCopy);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, target.width, target.height);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GlStateManager._glUseProgram(program);
        GlStateManager._bindTexture(texture.payload);
        GL20.glUniform1i(uDepth, 0);
        GL20.glUniform2f(uScreen, target.width, target.height);
        GL20.glUniform2f(uDhProj, dhA, dhB);
        GL20.glUniform2f(uMcProj, mcProjection.m22(), mcProjection.m32());
        GL20.glUniform1f(uClear, proxy.getDepthDirection().farDepth);
        GL20.glUniform1i(uZeroToOne, proxy.getDepthRange() == EDhApiDepthRange.ZERO_TO_POS_ONE ? 1 : 0);
        GL20.glUniform1f(uBias, BIAS);
        GL20.glUniform1i(uMcDepth, 1);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LESS);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(false, false, false, false);
        GlStateManager._glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GlStateManager._glBindVertexArray(previousVao);
        BufferUploader.invalidate();
        GlStateManager._bindTexture(previousTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(previousDepthTexture);
        GlStateManager._activeTexture(previousActive);
        GlStateManager._glUseProgram(previousProgram);
        return true;
    }

    /**
     * Keeps a copy of Minecraft's depth buffer that the shader can read while it writes into the real one.
     */
    private static boolean ensureDepthCopy(int width, int height) {
        if (depthCopy != 0 && width == copyWidth && height == copyHeight) {
            return true;
        }
        int previousActive = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (depthCopy != 0) {
            GlStateManager._deleteTexture(depthCopy);
        }
        depthCopy = GlStateManager._genTexture();
        GlStateManager._bindTexture(depthCopy);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null);
        GlStateManager._bindTexture(previous);
        GlStateManager._activeTexture(previousActive);
        copyWidth = width;
        copyHeight = height;
        return true;
    }

    private static int createProgram() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        if (vertex == 0 || fragment == 0) {
            return 0;
        }
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vertex);
        GL20.glAttachShader(id, fragment);
        GL30.glBindFragDataLocation(id, 0, "color");
        GL20.glLinkProgram(id);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            DhCompat.warn("depth shader link failed: " + GL20.glGetProgramInfoLog(id));
            GL20.glDeleteProgram(id);
            return 0;
        }
        uDepth = GL20.glGetUniformLocation(id, "DhDepth");
        uScreen = GL20.glGetUniformLocation(id, "ScreenSize");
        uDhProj = GL20.glGetUniformLocation(id, "DhProj");
        uMcProj = GL20.glGetUniformLocation(id, "McProj");
        uClear = GL20.glGetUniformLocation(id, "DhClearDepth");
        uZeroToOne = GL20.glGetUniformLocation(id, "DhZeroToOne");
        uBias = GL20.glGetUniformLocation(id, "Bias");
        uMcDepth = GL20.glGetUniformLocation(id, "McDepth");
        vao = GL30.glGenVertexArrays();
        return id;
    }

    private static int compile(int type, String source) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            DhCompat.warn("depth shader compile failed: " + GL20.glGetShaderInfoLog(id));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }
}
