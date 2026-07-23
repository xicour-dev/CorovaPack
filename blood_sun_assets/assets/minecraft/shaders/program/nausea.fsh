#version 120

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Progress;

varying vec2 texCoord;

void main() {
    // 1. Read the unmodified pixel color from the screen buffer
    vec4 sceneColor = texture2D(DiffuseSampler, texCoord);

    // 2. Separate luminance (perceived brightness)
    float luma = dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114));

    // 3. Apply a rich Crimson / Red-Convolved environmental wash
    vec3 baseBloodRed = vec3(luma * 1.45, luma * 0.12, luma * 0.08);

    // 4. Handle the Crimson Sun thresholding (luma > 0.82)
    vec3 finalColor;
    if (luma > 0.82) {
        float sunFactor = (luma - 0.82) / 0.18; // normalize 0.0 to 1.0
        vec3 sunCrimson = vec3(1.2, 0.05, 0.05) * luma;
        finalColor = mix(baseBloodRed, sunCrimson, sunFactor);
    } else {
        finalColor = baseBloodRed;
    }

    // 5. Use Progress to animate transitions smoothly.
    vec3 blendedColor = mix(sceneColor.rgb, finalColor, Progress);

    gl_FragColor = vec4(blendedColor, sceneColor.a);
}