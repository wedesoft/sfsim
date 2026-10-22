#version 450 core

uniform sampler2D flood;
uniform float shockwave_radius;
uniform float shockwave_strength;
uniform float time;

float shockfront(float distance, float Rn);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.71, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Fractal Brownian Motion for rich, detailed noise
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

vec4 shockwave_transfer(vec3 point, float shockwave_step, vec4 shockwave_scatter)
{
  vec4 apex = texture(flood, (point.xy / shockwave_radius + 1.0) / 2.0);
  float radial_distance = length(apex.xy - point.xy);
  float depth = apex.z + shockfront(radial_distance, apex.w);
  if (point.z <= depth) {
    float emission = shockwave_strength * shockwave_step * exp(1.0 * (point.z - depth)) * (1.0 - smoothstep(0.0, 0.25 * shockwave_radius, radial_distance));
    vec2 toApex = point.xy - apex.xy;
    float dist = length(toApex);
    vec2 dir = (dist > 0.0001) ? (toApex / dist) : vec2(0.0, 1.0);

    float noiseScale = 0.005;   // Frequency of the noise features
    float speed = 3.0;         // Speed of outward expansion

    vec2 noiseUV = dir * (dist * noiseScale - time * speed);

    float shockNoise = noise(noiseUV);

    emission *= shockNoise;
    shockwave_scatter += vec4(emission, emission, 0, 0);
  };
  return shockwave_scatter;
}
