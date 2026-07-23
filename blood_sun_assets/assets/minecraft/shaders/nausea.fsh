#version 150

uniform sampler2D InSampler;
uniform vec2 InSize;
uniform float Progress;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    // 1. Read the unmodified pixel color from the screen buffer (blocks, sky, clouds, entities, sun)
    vec4 sceneColor = texture(InSampler, texCoord);

    // 2. Separate luminance (perceived brightness)
    // Formula: Luma = 0.299 * R + 0.587 * G + 0.114 * B
    float luma = dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114));

    // 3. Apply a rich Crimson / Red-Convolved environmental wash
    // Standard environment gets a dark crimson hue while maintaining block details and shadows
    vec3 baseBloodRed = vec3(luma * 1.45, luma * 0.12, luma * 0.08);

    // 4. Handle the "Crimson Sun" requirement:
    // Any bright objects (e.g. the sun and skybox rays, which are highly luminous and close to white)
    // are isolated and tinted into a blazing red/crimson fire.
    // Thresholding: if luma > 0.82, we progressively increase the red intensity and colorize it.
    vec3 finalColor;
    if (luma > 0.82) {
        float sunFactor = (luma - 0.82) / 0.18; // normalize 0.0 to 1.0
        vec3 sunCrimson = vec3(1.2, 0.05, 0.05) * luma;
        finalColor = mix(baseBloodRed, sunCrimson, sunFactor);
    } else {
        finalColor = baseBloodRed;
    }

    // 5. Use Progress to animate transitions smoothly.
    // If the progress is 0, we render normal colors; if 1, we render complete crimson.
    // Progress is automatically handled by the client during Nausea transitions!
    vec3 blendedColor = mix(sceneColor.rgb, finalColor, Progress);

    fragColor = vec4(blendedColor, sceneColor.a);
}