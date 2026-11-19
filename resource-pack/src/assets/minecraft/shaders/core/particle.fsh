#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 sprite = texture(Sampler0, texCoord0);
    vec4 tint = vertexColor;
    if (all(lessThan(abs(sprite.gb - vec2(219.0 / 255.0, 11.0 / 255.0)), vec2(2.0 / 255.0)))) {
        sprite.rgb = vec3(sprite.r);
        // Native wire sparks use the red power ramp (zero blue, green at the highest levels).
        if (tint.b < 0.004 && tint.g <= tint.r * 0.23 + 0.004) {
            tint.rgb = tint.r * vec3(1.0, 0.72, 0.24);
        }
    }
    vec4 color = sprite * tint * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
