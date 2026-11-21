# HBM Far View for NTM Continuum

Keeps the mushroom cloud and the large blast effects of NTM - Continuum visible and animated from far away, beyond the render distance.

## What it does
- The nuclear mushroom cloud is sent to you and kept animated even when the explosion is far outside your loaded chunks.
- The cloud is no longer hidden by the fog at the edge of the render distance.
- The cloud is no longer cut off when its base leaves the screen (for example when you look up).
- The same applies to the Fleija, Solinium, Rainbow and Tom blast effects.
- NTM missiles are visible in flight from far away, beyond the render distance.
- The exhaust flame and smoke behind a missile is drawn at a long distance too. With distance it is thinner and larger.

The sky flash and the sound still arrive with their own delay, as in NTM - Continuum.

## Distant Horizons
Distant Horizons 3.3.3 is supported (optional). Far hills drawn by Distant Horizons hide the mushroom cloud and missiles correctly, also while you move around.

**Required setting:** Vanilla Fade Mode must be set to None. While it is on, Distant Horizons hides the cloud completely.
1. Open the Distant Horizons settings (the Distant Horizons button in the video settings, or its config screen from the mod list).
2. Go to Advanced Options > Graphics > Quality.
3. Set Vanilla Fade Mode to None.

The mod shows a chat hint when Vanilla Fade Mode is on.

Shader packs do not work correctly with Distant Horizons: the cloud is only hidden when terrain covers it completely. Using Distant Horizons without a shader pack (vanilla rendering) is recommended.

## Requirements
- Minecraft 1.21.1, NeoForge 21.1.228 or newer
- NTM - Continuum (hbm 1.0.5 or newer) and its requirement Golem Core

## Configuration
`config/hbm_farview-common.toml`
- `farTrackingRange`: how far (in blocks) the mushroom cloud, blast effects and missiles are sent to the player. Default and maximum 4096, 0 turns the range extension off. If you have problems such as lag, lower this value. An older config file may still contain 1024.

## Status
Alpha. Tested with a single player world and the bombs Ivy Mike and Tsar Bomba. The range of 4096 blocks, dedicated servers and shaders are not fully tested. Please report problems with your setup and the latest.log.

## Credits
The method for the extended entity tracking follows NTM Decentralized Edition by SecretAgent12 (LGPL-3.0). Built for NTM - Continuum.
This mod was written with Claude (Anthropic) under the direction of the project author.

License: LGPL-3.0
