#version 450 core

#define object_radius 2.0
#define SAMPLES 250
#define FALLOFF 10.0
#define SHOCKLENGTH 4.0

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

// Calculates shockfront x-offset for a spherical nose body.
// Returns x measured from nose apex (x = delta at y = 0, decreasing downstream).
float shockfront(float radial_distance, float mach, float curvature_radius)
{
  float mach2 = mach * mach;
  // 1. Standoff distance (Delta)
  float delta = curvature_radius * 0.143 * exp(3.24 / mach2);
  // 2. Shock radius of curvature at apex (Rc)
  float Rc = 1.143 * curvature_radius * exp(0.54 / pow(mach - 1.0, 1.2));
  // 3. Tangent of asymptotic Mach angle (tan_beta = 1 / sqrt(M^2 - 1))
  float tanb2 = 1.0 / (mach2 - 1.0);
  float cotb2 = 1.0 / tanb2;
  // 4. Hyperbolic profile term
  return delta - (sqrt(Rc * Rc + tanb2 * radial_distance * radial_distance) - Rc) * cotb2;
}

float hash3d(vec3 coordinates)
{
  float hashValue = coordinates.x + coordinates.y * 37.0 + coordinates.z * 521.0;
  return fract(sin(hashValue * 1.333) * 100003.9);
}

float interpolate_hermite(float value1, float value2, float factor)
{
    return mix(value1, value2, factor * factor * (3.0 - 2.0 * factor)); // Perform cubic Hermite interpolation
}

const vec2 vector01 = vec2(0.0, 1.0);

float noise(vec3 coordinates)
{
  vec3 fractional = fract(coordinates.xyz);
  vec3 integral = floor(coordinates.xyz);
  float hash000 = hash3d(integral);
  float hash100 = hash3d(integral + vector01.yxx);
  float hash010 = hash3d(integral + vector01.xyx);
  float hash110 = hash3d(integral + vector01.yyx);
  float hash001 = hash3d(integral + vector01.xxy);
  float hash101 = hash3d(integral + vector01.yxy);
  float hash011 = hash3d(integral + vector01.xyy);
  float hash111 = hash3d(integral + vector01.yyy);

  return interpolate_hermite(
      interpolate_hermite(interpolate_hermite(hash000, hash100, fractional.x), interpolate_hermite(hash010, hash110, fractional.x), fractional.y),
      interpolate_hermite(interpolate_hermite(hash001, hash101, fractional.x), interpolate_hermite(hash011, hash111, fractional.x), fractional.y),
      fractional.z);
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = 2.0 * object_radius * (fragCoord - vec2(iResolution.x * 0.8, iResolution.y * 0.5)) / iResolution.x;
    float M = iMouse.x / iResolution.x * 9.0 + 1.0;
    float Rn = 0.05;

    float strength = M / 10.0 * smoothstep(-SHOCKLENGTH, Rn, uv.x);

    vec3 col = vec3(0.0);
    for (int i=-SAMPLES; i<=SAMPLES; i++) {
      vec3 p = vec3(uv, (float(i) + hash3d(vec3(uv, 0.0)) - 0.5) * object_radius / float(SAMPLES));
      float front = shockfront(length(p.yz), M, Rn) + Rn;
      if (p.x < front) {
        float noiseScale = smoothstep(0.0, 3.0 * Rn, length(p.yz));
        float tailScale = 1.0 - smoothstep(0.0, Rn, length(p.yz));
        float dist = length(p.yz);
        vec2 dir = (dist > 0.0001) ? (p.yz / dist) : vec2(0.0, 1.0);
        float around = atan(dir.y, dir.x) / 6.28318530718 * 50.0;
        float along = p.x * 2.0 + M * 5.0 * iTime;
        vec2 noiseUV = vec2(around, along);
        float shockNoise = noise(vec3(noiseUV, 0.0)) * noiseScale + 1.0 - noiseScale;
        col += vec3(0.05) * exp(-FALLOFF * (front - uv.x)) * strength * shockNoise;
        if (length(p) > Rn && p.x < 0.0) {
          float tailnoise = noise(p * 25.0 + vec3(M * 10.0, 0.0, 0.0) * iTime) * tailScale;
          col += vec3(0.1) * tailnoise * strength;
        };
      };
    };
    if (length(uv) < Rn)
      col += vec3(0.2);

    fragColor = vec4(col,1.0);
}
