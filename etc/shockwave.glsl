#version 450 core

#define M_PI 3.1415926535897932384626433832795
#define SHOCKWAVE_COLOR vec3(0.2, 0.6, 1.0)
#define FLAME_CORE_COLOR vec3(1.0, 0.9, 0.3)
#define FLAME_TALE_COLOR vec3(1.0, 0.3, 0.0)
#define SHIP_COLOR vec3(0.7, 0.7, 0.8)
#define SIZE 0.05
#define SIGMA 0.2
#define OFFSET 0.05
#define ALPHA 2.0
#define STEP 0.0025
#define SHOCK 0.05

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

bool mask(vec2 p) {
  return p.y >= -SIZE && p.y <= SIZE;
}

float depth(vec2 p) {
  return 0.6;
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
  for (float x = -3.0 * SIGMA; x <= 3.0 * SIGMA; x += STEP) {
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
  for (float t = -3.0 * SIGMA; t <= x; t += STEP) {
    float f = (1.0 / (sqrt(2.0 * M_PI) * SIGMA)) * exp(-t * t / (2.0 * SIGMA * SIGMA));
    result += f * STEP;
  };
  return result;
}

float inverfdiff(float d) {
  float threshold = erf(2 * SIZE) - erf(0.0);
  for (float result=0.0; result < 3.0 * SIGMA; result += STEP) {
    threshold -= gauss(result, SIGMA) * STEP;
    threshold += gauss(result + 2 * SIZE, SIGMA) * STEP;
    if (threshold < d)
      return result;
  };
  return 3.0 * SIGMA;
}

float parabola(vec2 p) {
  vec2 filtered = filtered_depth(p);
  float falloff = inverfdiff(filtered.y);
  return filtered.x + OFFSET - ALPHA * falloff * falloff;
}

float line_sdf(vec2 p) {
  float x = 0.6;
  float y = clamp(p.y, -SIZE, SIZE);
  return length(p - vec2(x, y));
}

float wave(float x) {
  if (x < 0)
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
