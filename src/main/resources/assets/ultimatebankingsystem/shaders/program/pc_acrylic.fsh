#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 SigmaUv;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float totalWeight = 1.0;
    for (int tap = 1; tap <= 6; tap++) {
        float distance = float(tap) * 0.5;
        float weight = exp(-0.5 * distance * distance);
        vec2 offset = SigmaUv * distance;
        color += weight * (texture(DiffuseSampler, texCoord - offset).rgb
                         + texture(DiffuseSampler, texCoord + offset).rgb);
        totalWeight += 2.0 * weight;
    }
    fragColor = vec4(color / totalWeight, 1.0);
}
