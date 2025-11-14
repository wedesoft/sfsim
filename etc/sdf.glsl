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
uniform float pressure;

mat3 rotation_y(float angle);
mat3 rotation_z(float angle);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
vec2 ray_circle(vec2 centre, float radius, vec2 origin, vec2 direction);
vec2 subtract_interval(vec2 a, vec2 b);
float limit(float pressure);
vec4 plume_transfer(vec3 point, float plume_step, vec4 plume_scatter);

vec2 cylinder1_base_ = vec2(-10.9544,  4.3511);
vec2 cylinder2_base_ = vec2(-10.9544, -4.3511);

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
  float box_size = max(limit(pressure), nozzle) + WIDTH2 - nozzle;
  vec2 box = ray_box(vec3(END, -box_size, -box_size), vec3(START, box_size, box_size), origin, direction);
  vec2 engine = ray_box(engine_min, engine_max, origin, direction);
  vec2 cylinder1 = ray_circle(cylinder1_base_, RADIUS, origin.xy, direction.xy);
  vec2 cylinder2 = ray_circle(cylinder2_base_, RADIUS, origin.xy, direction.xy);
  vec2 joint = subtract_interval(subtract_interval(engine, cylinder1), cylinder2);
  vec3 color = vec3(0, 0, 0);
  if (joint.y > 0.0) {
    color = vec3(0.5);
    if (box.x + box.y > joint.x) {
      box.y = joint.x - box.x;
    };
  };
  vec4 plume_scatter = vec4(0, 0, 0, 1);
  plume_scatter = sample_plume(origin, direction, box, plume_scatter);
  color = plume_scatter.rgb + color * plume_scatter.a;
  fragColor = vec4(color, 1.0);
}
