#version 150

uniform sampler2D In;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(In, texCoord);

    // Calculate the luminance of the current pixel
    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Create a deep blood-red crimson hue
    vec3 crimson = vec3(0.95, 0.08, 0.12);

    // Mix the original scene color with a crimson-shaded luminance value.
    // Retaining 25% of original colors and 75% crimson wash produces a dramatic,
    // beautiful environmental red tint while maintaining block textures and details.
    vec3 tinted = mix(color.rgb, crimson * luminance * 1.6, 0.75);

    fragColor = vec4(tinted, color.a);
}
