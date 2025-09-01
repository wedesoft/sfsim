#version 410 core

#define M_PI 3.1415926535897932384626433832795
#define HASHSCALE 0.1031

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW

uniform vec2 iResolution;
uniform float iTime;

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

// https://www.shadertoy.com/view/WsjGRz
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

float current_bulge(float phase) {
  return abs(sin(phase));
}

float current_dilation(float phase) {
  return 1.0 + 0.02 * phase;
}

float smooth_bulge(float phase) {
  float weight = exp(-phase * 0.1);
  float growth = current_dilation(phase);
  return growth * (current_bulge(phase) * weight + (1.0 - weight));
}

float current_radius(float min_radius, float bulge, float phase) {
  return min_radius + bulge * smooth_bulge(phase);
}

float intersection(float radius, float radial_coord) {
  return sqrt(radius * radius - radial_coord * radial_coord);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0);
  float left = uv.x;
  float t = iTime;
  uv.y += .01*perlin(t*67.+left)*(.1+left);
  uv.y += .005*perlin(t*101.+left)*(.1+left);
  float omega = 24.0;
  float phase = omega * uv.x + M_PI / 4;
  float min_radius = 0.1;
  float bulge = 0.05;
  float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
  float radius = current_radius(min_radius, bulge, phase);
  float radial_coord = abs(uv.y);
  float cross_section = radius > radial_coord ? intersection(radius, radial_coord) : 0.0;
  vec3 background = vec3(0.12, 0.27, 0.42);
  vec3 fringe_color = vec3(0.9, 0.5, 0.6);
  float smoothing = 0.03;
  float flame_frequency_longitudinal = 20.0;
  float flame_frequency_lateral = 40.0;
  float brightness = fbm(vec2(uv.x * flame_frequency_longitudinal - t * 200.0, uv.y * flame_frequency_lateral * min_radius / radius));
  brightness = clamp(brightness, 0.0, 1.0);
  float variation = 0.08;
  cross_section += brightness * variation - variation * 0.5;
  float dilation = current_dilation(phase);
  float strength = 4.0 * (1.0 - smoothstep(radius - smoothing, radius, abs(uv.y))) / (dilation * dilation);
  vec3 luminocity = strength * cross_section * (fringe_color - background);
  float diamond_front_length = min_radius / (bulge * omega);
  float diamond_back_length = diamond_front_length * 0.7;
  float tail_start = 0.3 * diamond_front_length;
  float tail_length = 0.8 * diamond_front_length;
  float diamond_length = diamond_longitudinal > 0.0 ? diamond_back_length : diamond_front_length;
  float diamond_radius = min_radius * max(0.0, 1.0 - abs(diamond_longitudinal / omega) / diamond_length);
  float diamond_strength = min(1.0, max(0.0, 1.0 - (tail_start + diamond_longitudinal / omega) / tail_length));
  float blur = dilation - 1.0;
  diamond_strength = 3.0 * diamond_strength * (1.0 - smoothstep(diamond_radius - blur, diamond_radius, abs(uv.y)));
  float diamond_cross_section = sqrt(max(0.0, diamond_radius * diamond_radius - radial_coord * radial_coord));
  float diamond = 1.5 * diamond_cross_section * diamond_strength;
  vec3 diamond_color = diamond * vec3(0.98, 0.93, 1.0);
  vec3 color = background + luminocity + diamond_color;
  fragColor = vec4(color, 1);
}
