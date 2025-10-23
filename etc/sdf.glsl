#version 410 core

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW
// 3D perlin noise: https://www.shadertoy.com/view/4djXzz

#define M_PI 3.1415926535897932384626433832795
#define FOV (60.0 * M_PI / 180.0)
#define F (1.0 / tan(FOV / 2.0))
#define DIST 2.0
#define WIDTH2 0.4
#define SAMPLES 100
#define SPEED 50.0
#define START -1.0
#define END 2.5

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

uniform float nozzle;
uniform float min_limit;
uniform float max_slope;
uniform float omega_factor;

mat3 rotation_x(float angle);
mat3 rotation_y(float angle);
mat3 rotation_z(float angle);
float noise3d(vec3 coordinates);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float sdf_circle(vec2 point, vec2 center, float radius);
float sdf_rectangle(vec2 point, vec2 rectangle_min, vec2 rectangle_max);
vec2 ray_circle(vec2 centre, float radius, vec2 origin, vec2 direction);
vec2 subtract_interval(vec2 a, vec2 b);

float sdfEngine(vec2 cylinder1_base, vec2 cylinder2_base, vec3 p) {
  if (abs(p.z) > WIDTH2) {
    return abs(p.z) - WIDTH2;
  }
  if (abs(p.y) <= nozzle) {
    vec2 base = p.y > 0.0 ? cylinder1_base : cylinder2_base;
    return 0.15 - length(p.xy - base);
  }
  vec2 o = vec2(min(p.x - (cylinder1_base.x - 0.15), p.x), nozzle);
  return length(o - vec2(p.x, abs(p.y)));
}

float pressure()
{
  float slider = iMouse.y / iResolution.y;
  return pow(0.001, slider);
}

float limit(float pressure)
{
  return min_limit * sqrt(1.0 / pressure);
}

float bulge(float pressure, float x);

vec2 envelope(float pressure, float x) {
  float bulge = bulge(pressure, x);
  return vec2(bulge + WIDTH2 - nozzle, bulge);
}

float diamond(float pressure, vec2 uv)
{
  float limit = limit(pressure);
  float diamond;
  if (nozzle > limit) {
    float bulge = nozzle - limit;
    float omega = omega_factor * bulge;
    float phase = omega * uv.x; //  + M_PI / 2.0;
    float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
    float diamond_front_length = limit / (bulge * omega);
    float diamond_back_length = diamond_front_length * 0.3;
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
  float aspect = iResolution.x / iResolution.y;
  vec2 uv = (fragCoord.xy / iResolution.xy * 2.0 - 1.0) * vec2(aspect, 1.0);
  float ry = iMouse.x / iResolution.x;
  // float ry = 0.3;
  mat3 rotation = rotation_z(0.1 * M_PI) * rotation_y((2.0 * ry + 1.0) * M_PI);
  vec3 light = rotation * normalize(vec3(1.0, 1.0, 0.0));
  vec3 origin = rotation * vec3(0.0, 0.0, -DIST);
  vec3 direction = normalize(rotation * vec3(uv, F));
  vec3 engine_min = vec3(START, -nozzle, -WIDTH2);
  vec3 engine_max = vec3(START + 0.22, nozzle, WIDTH2);
  float pressure = pressure();
  // float pressure = 1.0;
  float box_size = max(limit(pressure), nozzle) + WIDTH2 - nozzle;
  vec2 box = ray_box(vec3(START, -box_size, -box_size), vec3(END, box_size, box_size), origin, direction);
  vec2 engine = ray_box(engine_min, engine_max, origin, direction);
  vec2 cylinder1_base = vec2(START + 0.22, 0.22);
  vec2 cylinder2_base = vec2(START + 0.22, -0.22);
  vec2 cylinder1 = ray_circle(cylinder1_base, 0.2, origin.xy, direction.xy);
  vec2 cylinder2 = ray_circle(cylinder2_base, 0.2, origin.xy, direction.xy);
  vec2 joint = subtract_interval(subtract_interval(engine, cylinder1), cylinder2);
  vec3 color = vec3(0, 0, 0);
  if (joint.y > 0.0) {
    float diffuse = 0.5;
    color = vec3(1.0) * (diffuse * 0.9 + 0.1);
    if (box.x + box.y > joint.x) {
      box.y = joint.x - box.x;
    };
  };
  if (box.y > 0.0) {
    float ds = box.y / float(SAMPLES);
    for (int i = 0; i <= SAMPLES; i++)
    {
      float s = box.x + float(i) * ds;
      vec3 p = origin + direction * s;
      if (p.x >= engine_min.x) {
        vec2 envelope = envelope(pressure, p.x - engine_max.x);
        float engine_pos = clamp((p.x - engine_max.x + 0.2) / 0.2, 0.0, 1.0);
        float transition = clamp((limit(pressure) - min_limit) / (nozzle - min_limit), 0.0, 1.0);
        float circular = clamp((p.x - engine_max.x) / (END - engine_max.x), 0.0, 1.0);
        float radius = 0.5 * (envelope.x + envelope.y);
        engine_pos = clamp(engine_pos + transition, 0.0, 1.0);
        float slider1 = iMouse.x / iResolution.x;
        float slider2 = iMouse.y / iResolution.y;
        //float distortion1 = max(0.0, 5.0 * p.y * p.z * (slider1 - 0.5));
        //float distortion2 = max(0.0, 5.0 * p.y * (slider2 - 0.5));
        float distortion1 = 0.0;
        float distortion2 = 0.0;
        float baseSdf = sdfEngine(cylinder1_base, cylinder2_base, p);
        float shapeMix = mix(sdf_rectangle(p.zy, -envelope, +envelope), sdf_circle(p.zy, vec2(0, 0), radius), circular);
        float sdf = mix(baseSdf, shapeMix, engine_pos) + distortion1 + distortion2;
        if (sdf < 0.0) {
          float dz = mix(WIDTH2, envelope.x, engine_pos);
          float dy = mix(0.2 - 0.15, envelope.y, engine_pos);
          float density = 0.2 / (dz * dy) * (1.0 - circular);
          float fringe = max(1.0 + sdf / 0.1, 0.0);
          vec3 scale = 20.0 * vec3(0.1, nozzle / envelope.y, nozzle / envelope.x);
          float attenuation = 0.7 + 0.3 * noise3d(p * scale + iTime * vec3(-SPEED, 0.0, 0.0));
          vec3 flame_color = mix(vec3(0.6, 0.6, 1.0), mix(vec3(0.90, 0.59, 0.80), vec3(0.50, 0.50, 1.00), fringe), pressure);
          float diamond = mix(0.2, diamond(pressure, vec2(p.x - engine_max.x, max(0.0, sdf + dy))), engine_pos);
          color = color * pow(0.2, ds * density);
          color += flame_color * ds * density * attenuation;
          color += diamond * density * 10.0 * ds * vec3(1, 1, 1) * attenuation;
        };
      };
    };
  };
  fragColor = vec4(color, 1.0);
}
