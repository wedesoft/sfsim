#version 450 core

#define M_PI 3.1415926535897932384626433832795
#define SHOCKWAVE_COLOR vec3(0.2, 0.6, 1.0)
#define FLAME_CORE_COLOR vec3(1.0, 0.9, 0.3)
#define FLAME_TALE_COLOR vec3(1.0, 0.3, 0.0)
#define SHIP_COLOR vec3(0.7, 0.7, 0.8)
#define SIZE 0.1
#define PLUME 0.5
#define SIGMA 0.02
#define OFFSET 0.05
#define ALPHA 2.0
#define STEP 0.01
#define SHOCK 0.05
#define XPOS 0.6

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

float angle()
{
  return (iMouse.x / iResolution.x - 0.5) * 45.0 / 180.0 * M_PI;
}

bool mask(vec2 p) {
  return p.y >= -SIZE && p.y <= SIZE;
}

float depth(vec2 p) {
  return XPOS + p.y * sin(angle());
}

float bell(float x, float sigma) {
  return exp(-x * x / (2.0 * sigma * sigma));
}

float gauss(float x, float sigma) {
  return (1.0 / (sqrt(2.0 * M_PI) * sigma)) * bell(x, sigma);
}

vec2 filtered_depth(vec2 p) {
  float sum = 0.0;
  float weight = 0.0;
  float convolution = 0.0;
  for (float x = -PLUME; x <= PLUME; x += STEP) {
    float f = gauss(x, SIGMA) * STEP;
    if (mask(p + vec2(0, x))) {
      weight += f;
      convolution += depth(p + vec2(0, x)) * f;
    };
    sum += f;
  };
  return vec2(convolution / weight, weight);
}

// Integral of Gaussian (error function) with value SIGMA
float erf(float x) {
  float result = 0.0;
  for (float t = -PLUME - 2.0 * SIZE; t <= x; t += STEP) {
    float f = gauss(t, SIGMA) * STEP;
    result += f;
  };
  return result;
}

float inverfdiff(float d) {
  float threshold = erf(-PLUME) - erf(- PLUME - 2.0 * SIZE);
  for (float result = - PLUME - 2.0 * SIZE; result <= 0; result += STEP) {
    threshold -= gauss(result, SIGMA) * STEP;
    threshold += gauss(result + 2 * SIZE, SIGMA) * STEP;
    if (threshold >= d)
      return max(-result - 2.0 * SIZE, 0);
  };
  return 0.0;
}

float parabola(vec2 p) {
  vec2 filtered = filtered_depth(p);
  float falloff = inverfdiff(filtered.y);
  return filtered.x + OFFSET - ALPHA * falloff * falloff;
}

float line_sdf(vec2 p) {
  float a = angle();
  float cos_a = cos(a);
  float sin_a = sin(a);
  mat2 m = mat2(cos_a, sin_a, -sin_a, cos_a);
  vec2 ps = m * (p - vec2(XPOS, 0));
  vec2 q = vec2(0.0, clamp(ps.y, -SIZE, SIZE));
  return length(ps - q);
}

float wave(float x) {
  if (x < 0.0)
    return 0.0;
  else
    return x * exp(1 - x);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 uv = (fragCoord - iResolution.xy / 2.0) / (iResolution.x / 2.0);
  float sdf = line_sdf(uv);
  if (sdf < STEP)
    fragColor = vec4(SHIP_COLOR, 1);
  else {
    float parabola = parabola(uv);
    float depth = parabola - uv.x;
    float strength = wave(depth / SHOCK);
    fragColor = vec4(0.5 * FLAME_TALE_COLOR * strength, 1);
  };
}
