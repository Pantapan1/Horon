package com.voxelhorizon.nativegame

import com.badlogic.gdx.graphics.glutils.ShaderProgram

object VoxelShaders {
    // Vertex attributes: a_position (vec3), a_texCoord0 (vec2), a_color (vec4: R=skyLight*AO, G=blockLight*AO, B=faceShade, A=flags)
    // flags (a_color.a): 0.0 = normal, 0.25 = foliage (season tint + wind sway), 0.50 = water (wave + glint), 0.75 = emissive (lava/glowstone/portal)
    private const val VERT_SRC = """
        attribute vec3 a_position;
        attribute vec2 a_texCoord0;
        attribute vec4 a_color;

        uniform mat4 u_projTrans;
        uniform float u_time;
        uniform vec3 u_camPos;

        varying vec2 v_texCoord;
        varying vec4 v_data;
        varying vec3 v_worldPos;
        varying float v_dist;

        void main() {
            vec3 pos = a_position;
            float flag = a_color.a;

            // Wind sway for foliage/leaves (flag ~ 0.25)
            if (flag > 0.15 && flag < 0.35) {
                float sway = sin(u_time * 2.4 + pos.x * 0.7 + pos.z * 0.7) * 0.045;
                pos.x += sway;
                pos.z += sway * 0.6;
            }
            // Living water waves (flag ~ 0.50)
            if (flag > 0.40 && flag < 0.60) {
                pos.y += sin(u_time * 3.0 + pos.x * 1.1 + pos.z * 0.9) * 0.055;
            }

            v_worldPos = pos;
            v_texCoord = a_texCoord0;
            v_data = a_color;
            v_dist = length(pos - u_camPos);
            gl_Position = u_projTrans * vec4(pos, 1.0);
        }
    """

    private const val FRAG_SRC = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        uniform sampler2D u_texture;
        uniform float u_dayFactor;
        uniform float u_time;
        uniform vec3 u_fogColor;
        uniform float u_fogStart;
        uniform float u_fogEnd;
        uniform vec3 u_seasonTint;
        uniform vec3 u_handLightPos;
        uniform vec3 u_handLightColor;
        uniform float u_handLightIntensity;
        uniform float u_alpha;

        varying vec2 v_texCoord;
        varying vec4 v_data;
        varying vec3 v_worldPos;
        varying float v_dist;

        void main() {
            vec2 uv = v_texCoord;
            float flag = v_data.a;

            // Animated UV scroll for water/lava
            vec4 texCol = texture2D(u_texture, uv);
            if (texCol.a < 0.25) discard;

            // Instant GPU Season tint on grass/leaves!
            if (flag > 0.15 && flag < 0.35) {
                texCol.rgb *= u_seasonTint;
            }

            // Two-channel lighting: Sky light * Day/Night + Warm Block Light + AO + Face Shade
            float skyL = v_data.r * (0.22 + 0.78 * u_dayFactor);
            float blkL = v_data.g;
            float shade = v_data.b;

            vec3 lightCol = vec3(skyL * 0.96, skyL * 0.98, skyL * 1.04)
                          + vec3(blkL * 1.12, blkL * 0.88, blkL * 0.62);

            // Real-time colored Dynamic Hand Light (Torch / Glowstone / Lava / Portal)
            if (u_handLightIntensity > 0.01) {
                float d = length(v_worldPos - u_handLightPos);
                float att = clamp(1.0 - d / 11.5, 0.0, 1.0);
                lightCol += u_handLightColor * (att * att * u_handLightIntensity);
            }

            // Emissive blocks (glowstone, lava, portal) stay bright
            if (flag > 0.65) {
                lightCol = max(lightCol, vec3(1.15, 1.10, 1.05));
            }

            vec3 lit = texCol.rgb * clamp(lightCol * shade, vec3(0.08), vec3(1.35));

            // Sparkling sun glint on water!
            if (flag > 0.40 && flag < 0.60) {
                float glint = pow(max(0.0, sin(v_worldPos.x * 2.5 + u_time * 3.5) * cos(v_worldPos.z * 2.5 - u_time * 2.7)), 6.0);
                lit += vec3(0.28, 0.38, 0.45) * glint * (0.3 + 0.7 * u_dayFactor);
            }

            // Smooth distance fog
            float fogF = clamp((v_dist - u_fogStart) / max(1.0, u_fogEnd - u_fogStart), 0.0, 1.0);
            vec3 finalRgb = mix(lit, u_fogColor, fogF * fogF);
            gl_FragColor = vec4(finalRgb, texCol.a * u_alpha);
        }
    """

    fun createTerrainShader(): ShaderProgram {
        ShaderProgram.pedantic = false
        val sp = ShaderProgram(VERT_SRC, FRAG_SRC)
        if (!sp.isCompiled) {
            throw RuntimeException("VoxelShaders compile error: " + sp.log)
        }
        return sp
    }
}
