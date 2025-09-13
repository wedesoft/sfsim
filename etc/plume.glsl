#version 410 core

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW

#define M_PI 3.1415926535897932384626433832795
#define NOZZLE 0.2
#define SCALING 0.1
#define HASHSCALE 0.1031

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

float hash(float p)
{
  vec3 p3  = fract(vec3(p) * HASHSCALE);
  p3 += dot(p3, p3.yzx + 19.19);
  return fract((p3.x + p3.y) * p3.z);
}

float fade(float t) { return t*t*t*(t*(6.*t-15.)+10.); }

float grad(float hash, float p)
{
  int i = int(1e4*hash);
  return (i & 1) == 0 ? p : -p;
}

float perlin(float p)
{
  float pi = floor(p), pf = p - pi, w = fade(pf);
  return mix(grad(hash(pi), pf), grad(hash(pi + 1.0), pf - 1.0), w) * 2.0;
}

float hash21(vec2 v) {
  return fract(sin(dot(v, vec2(12.9898, 78.233))) * 43758.5453123);
}

float noise(vec2 uv) {
  vec2 f = fract(uv);
  vec2 i = floor(uv);
  f = f * f * (3. - 2. * f);
  return mix(
      mix(hash21(i), hash21(i + vec2(1,0)), f.x),
      mix(hash21(i + vec2(0,1)), hash21(i + vec2(1,1)), f.x), f.y);
}

float fbm(vec2 n) {
  float total = 0.0, amplitude = 0.5;
  for (int i = 0; i < 3; i++) {
    total += noise(n) * amplitude;
    n = n * 2.0;
    amplitude *= 0.5;
  }
  return total;
}


float sqr(float x)
{
  return x * x;
}

float pressure()
{
  float slider = iMouse.y / iResolution.y;
  return pow(0.001, slider);
}

float limit(float pressure)
{
  return SCALING * sqrt(1.0 / pressure);
}

float bumps(float x)
{
  float pressure = pressure();
  float limit = limit(pressure);
  if (NOZZLE < limit) {
    float c = 0.4;
    float log_c = log(c);
    float start = log((limit - NOZZLE) / limit) / log_c;
    return limit - limit * pow(c, start + x);
  } else {
    float bulge = NOZZLE - limit;
    float omega = 100.0 * bulge;
    float bumps = bulge * abs(cos(x * omega));
    return limit + bumps;
  };
}

float fringe(vec2 uv)
{
  float pressure = pressure();
  float radius = bumps(uv.x);
  float dist = abs(uv.y) - radius;
  return mix(0.5, max(1.0 - abs(dist) / 0.1, 0.0), pressure);
}

float flame(vec2 uv)
{
  float bumps = bumps(uv.x);
  float flame_frequency_longitudinal = 20.0;
  float flame_frequency_lateral = 40.0;
  float brightness = fbm(vec2(uv.x * flame_frequency_longitudinal - iTime * 200.0, uv.y * flame_frequency_lateral * NOZZLE / bumps));
  return clamp(brightness, 0.0, 1.0);
}

float diamond(vec2 uv)
{
  float pressure = pressure();
  float limit = limit(pressure);
  float diamond;
  if (NOZZLE > limit) {
    float bulge = NOZZLE - limit;
    float omega = 100.0 * bulge;
    float phase = omega * uv.x + M_PI / 2.0;
    float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
    float diamond_front_length = limit / (bulge * omega);
    float diamond_back_length = diamond_front_length * 0.7;
    float tail_start = 0.3 * diamond_front_length;
    float tail_length = 0.8 * diamond_front_length;
    float diamond_length = diamond_longitudinal > 0.0 ? diamond_back_length : diamond_front_length;
    float diamond_radius = limit * max(0.0, 1.0 - abs(diamond_longitudinal / omega) / diamond_length);
    float extent = 1.0;
    float decay = max(0.0, 1.0 - abs(diamond_longitudinal / extent));
    diamond = 0.1 / diamond_front_length * (1.0 - smoothstep(diamond_radius - 0.05, diamond_radius, abs(uv.y))) * decay;
  } else {
    diamond = 0.0;
  };
  return diamond;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float fov = 2.0;
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0) * fov;
  uv.y += .01*perlin(iTime*67.+uv.x)*(.1+uv.x);
  uv.y += .005*perlin(iTime*101.+uv.x)*(.1+uv.x);
  uv.x *= (1.0 + 0.01 * perlin(iTime*137.0));
  float radius = bumps(uv.x);
  bool inside = abs(uv.y) <= radius;
  vec3 color;
  if (inside) {
    float a = radius * radius;
    float d = sqrt(radius * radius - uv.y * uv.y);
    float diamond = diamond(uv);
    float flame = flame(uv);
    float fringe = fringe(uv);
    vec3 flame_color = mix(vec3(0.90, 0.59, 0.80), vec3(0.50, 0.50, 1.00), fringe);
    color = vec3(1, 1, 1) * diamond * 0.7 + 0.1 * (1.5 + 0.75 * flame) * flame_color * d / a;
  } else {
    color = vec3(0, 0, 0);
  };
  fragColor =  vec4(color, 1);
}
