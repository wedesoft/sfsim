#version 410 core

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW
// 3D perlin noise: https://www.shadertoy.com/view/4djXzz

// circle: x = -10.9544, z =  4.3511, radius = 3.9011
// circle: x = -10.9544, z = -4.3511, radius = 3.9011
// engine: x = -11.18 to -7.5047, z up to 2.5296 or maybe 2.7549

#define M_PI 3.1415926535897932384626433832795
#define FOV (60.0 * M_PI / 180.0)
#define F (1.0 / tan(FOV / 2.0))
#define DIST 30.0
#define WIDTH2 7.4266
#define SPEED 50.0
#define START -7.5047
#define ENGINE -11.18
#define OFFSET -15.0
#define END -50.0
#define ENGINE_SIZE (START - ENGINE)
#define ENGINE_STEP 0.2
#define BASE_DENSITY 2.0
#define DIAMOND_DENSITY 5.0
#define RADIUS 3.9011
#define LAYER 0.6

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

uniform float nozzle;
uniform float min_limit;
uniform float max_slope;
uniform float omega_factor;
uniform float diamond_strength;

mat3 rotation_x(float angle);
mat3 rotation_y(float angle);
mat3 rotation_z(float angle);
float noise3d(vec3 coordinates);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float sdf_circle(vec2 point, vec2 center, float radius);
float sdf_rectangle(vec2 point, vec2 rectangle_min, vec2 rectangle_max);
vec2 ray_circle(vec2 centre, float radius, vec2 origin, vec2 direction);
vec2 subtract_interval(vec2 a, vec2 b);
float limit(float pressure);
float plume_phase(float x, float limit);
float plume_omega(float limit);
float bulge(float pressure, float x);
float diamond(float pressure, vec2 uv);

float sdfEngine(vec2 cylinder1_base, vec2 cylinder2_base, vec3 p) {
  if (abs(p.z) > WIDTH2) {
    return abs(p.z) - WIDTH2;
  }
  if (abs(p.y) <= nozzle) {
    vec2 base = p.y > 0.0 ? cylinder1_base : cylinder2_base;
    return RADIUS - LAYER - length(p.xy - base);
  }
  vec2 o = vec2(min(p.x - (cylinder1_base.x - RADIUS + LAYER), p.x), nozzle);
  return length(o - vec2(p.x, abs(p.y)));
}

float pressure()
{
  float slider = iMouse.y / iResolution.y;
  return pow(0.001, slider);
}

vec2 envelope(float pressure, float x) {
  float bulge = bulge(pressure, x);
  return vec2(bulge + WIDTH2 - nozzle, bulge);
}

vec2 cylinder1_base = vec2(-10.9544,  4.3511);
vec2 cylinder2_base = vec2(-10.9544, -4.3511);

vec4 plume_transfer(vec3 point, float plume_step, vec4 plume_scatter)
{
  float pressure = pressure();
  float transition = clamp((limit(pressure) - min_limit) / (nozzle - min_limit), 0.0, 1.0);
  vec2 envelope = envelope(pressure, START - point.x - mix(ENGINE_SIZE, 0.0, transition));
  float engine_pos = clamp((START - point.x) / ENGINE_SIZE, 0.0, 1.0);
  float radius = 0.5 * (envelope.x + envelope.y);
  // float slider1 = iMouse.x / iResolution.x;
  // float slider2 = iMouse.y / iResolution.y;
  // //float distortion1 = max(0.0, 5.0 * point.y * point.z * (slider1 - 0.5));
  // //float distortion2 = max(0.0, 5.0 * point.y * (slider2 - 0.5));
  // float distortion1 = 0.0;
  // float distortion2 = 0.0;
  float baseSdf = sdfEngine(cylinder1_base, cylinder2_base, point);
  float fade = clamp((point.x - END) / (START - END), 0.0, 1.0);
  float shapeMix = mix(sdf_circle(point.zy, vec2(0, 0), radius), sdf_rectangle(point.zy, -envelope, +envelope), fade);
  float sdf = mix(baseSdf, shapeMix, mix(engine_pos, 1.0, transition)); // + distortion1 + distortion2;
  if (sdf < 0.0) {
    float dz = mix(WIDTH2, envelope.x, engine_pos);
    float dy = mix(LAYER, envelope.y, engine_pos);
    float density = BASE_DENSITY / (dz * dy) * fade;
    float fringe = max(1.0 + sdf / 1.0, 0.0);
    vec3 scale = 2.0 * vec3(0.1, nozzle / envelope.y, nozzle / envelope.x);
    float attenuation = 0.7 + 0.3 * noise3d(point * scale + iTime * vec3(SPEED, 0.0, 0.0));
    vec3 flame_color = mix(vec3(0.6, 0.6, 1.0), mix(vec3(0.90, 0.59, 0.80), vec3(0.50, 0.50, 1.00), fringe), pressure);
    float diamond = mix(diamond_strength, diamond(pressure, vec2(START - point.x - mix(ENGINE_SIZE, 0.0, transition), max(0.0, sdf + dy))), mix(engine_pos, 1.0, transition));
    plume_scatter.a += plume_step * density;
    plume_scatter.rgb += flame_color * plume_step * density * attenuation;
    plume_scatter.rgb += DIAMOND_DENSITY * diamond * density * plume_step * vec3(1, 1, 1) * attenuation;
  };
  return plume_scatter;
};

vec4 sample_plume(vec3 origin, vec3 direction, vec2 plume_box, vec4 plume_scatter)
{
  float l = plume_box.x;
  while (l < plume_box.x + plume_box.y) {
    vec3 point = origin + l * direction;
    plume_scatter = plume_transfer(point, ENGINE_STEP, plume_scatter);
    l += ENGINE_STEP;
  }
  return plume_scatter;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float aspect = iResolution.x / iResolution.y;
  vec2 uv = (fragCoord.xy / iResolution.xy * 2.0 - 1.0) * vec2(aspect, 1.0);
  float ry = iMouse.x / iResolution.x;
  mat3 rotation = rotation_z(-0.1 * M_PI) * rotation_y((2.0 * ry + 1.0) * M_PI);
  vec3 light = rotation * normalize(vec3(1.0, 1.0, 0.0));
  vec3 origin = rotation * vec3(0.0, 0.0, -DIST) + vec3(OFFSET, 0.0, 0.0);
  vec3 direction = normalize(rotation * vec3(uv, F));
  vec3 engine_min = vec3(ENGINE, -nozzle, -WIDTH2);
  vec3 engine_max = vec3(START, nozzle, WIDTH2);
  float pressure = pressure();
  // float pressure = 1.0;
  float box_size = max(limit(pressure), nozzle) + WIDTH2 - nozzle;
  vec2 box = ray_box(vec3(END, -box_size, -box_size), vec3(START, box_size, box_size), origin, direction);
  vec2 engine = ray_box(engine_min, engine_max, origin, direction);
  vec2 cylinder1 = ray_circle(cylinder1_base, RADIUS, origin.xy, direction.xy);
  vec2 cylinder2 = ray_circle(cylinder2_base, RADIUS, origin.xy, direction.xy);
  vec2 joint = subtract_interval(subtract_interval(engine, cylinder1), cylinder2);
  vec3 color = vec3(0, 0, 0);
  if (joint.y > 0.0) {
    color = vec3(0.5);
    if (box.x + box.y > joint.x) {
      box.y = joint.x - box.x;
    };
  };
  vec4 plume_scatter = vec4(0, 0, 0, 0);
  plume_scatter = sample_plume(origin, direction, box, plume_scatter);
  color = plume_scatter.rgb + color * (1.0 - plume_scatter.a);
  fragColor = vec4(color, 1.0);
}
