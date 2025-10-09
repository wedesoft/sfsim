#version 410 core

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW
// 3D perlin noise: https://www.shadertoy.com/view/4djXzz

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

#define M_PI 3.1415926535897932384626433832795
#define FOV (60.0 * M_PI / 180.0)
#define F (1.0 / tan(FOV / 2.0))
#define DIST 2.0
#define WIDTH2 0.4
#define NOZZLE 0.16
#define SCALING 0.1
#define SAMPLES 100
#define OMEGA_FACTOR 50.0
#define SPEED 50.0
#define START -1.0
#define END 2.5

// rotation around x axis
mat3 rotation_x(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(
    1, 0, 0,
    0, c, s,
    0, -s, c
  );
}

// rotation around y axis
mat3 rotation_y(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(
    c, 0, -s,
    0, 1, 0,
    s, 0, c
  );
}

// rotation around z axis
mat3 rotation_z(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(
    c, s, 0,
    -s, c, 0,
    0, 0, 1
  );
}

vec2 ray_box(vec3 box_min, vec3 box_max,
             vec3 origin, vec3 direction,
             out vec3 normal)
{
    vec3 factors1 = (box_min - origin) / direction;
    vec3 factors2 = (box_max - origin) / direction;

    vec3 intersections1 = min(factors1, factors2);
    vec3 intersections2 = max(factors1, factors2);

    float near = max(max(max(intersections1.x, intersections1.y), intersections1.z), 0.0);
    float far  = min(min(intersections2.x, intersections2.y), intersections2.z);

    if (far < near) {
        normal = vec3(0.0);
        return vec2(-1.0, -1.0);
    }

    if (near == intersections1.x) {
        normal = (factors1.x > factors2.x) ? vec3(1.0, 0.0, 0.0)
                                           : vec3(-1.0, 0.0, 0.0);
    } else if (near == intersections1.y) {
        normal = (factors1.y > factors2.y) ? vec3(0.0, 1.0, 0.0)
                                           : vec3(0.0, -1.0, 0.0);
    } else {
        normal = (factors1.z > factors2.z) ? vec3(0.0, 0.0, 1.0)
                                           : vec3(0.0, 0.0, -1.0);
    }

    return vec2(near, max(far - near, 0.0));
}

vec2 intersectCylinder(vec3 origin, vec3 direction,
                       vec3 base, vec3 axis, float r,
                       out vec3 normal)
{
    vec3 axisN = normalize(axis);

    vec3 d = direction - dot(direction, axisN) * axisN;

    vec3 deltaP = origin - base;

    vec3 m = deltaP - dot(deltaP, axisN) * axisN;

    float A = dot(d, d);
    float B = 2.0 * dot(d, m);
    float C = dot(m, m) - r * r;

    float disc = B * B - 4.0 * A * C;
    if (disc < 0.0) {
        normal = vec3(0.0);
        return vec2(-1.0, -1.0);
    }

    float sqrtDisc = sqrt(disc);
    float t0 = (-B - sqrtDisc) / (2.0 * A);
    float t1 = (-B + sqrtDisc) / (2.0 * A);

    if (t1 < 0.0) {
        normal = vec3(0.0);
        return vec2(-1.0, -1.0);
    }

    float tEnter = max(t0, 0.0);
    float tExit  = t1;
    float segLen = tExit - tEnter;

    if (segLen <= 0.0) {
        normal = vec3(0.0);
        return vec2(-1.0, -1.0);
    }

    vec3 hitPoint = origin + t1 * direction;

    vec3 axisProj = base + dot(hitPoint - base, axisN) * axisN;

    normal = normalize(hitPoint - axisProj);

    return vec2(tEnter, segLen);
}

vec2 subtractInterval(vec2 a, vec2 b)
{
    float aStart = a.x;
    float aEnd   = a.x + a.y;
    float bStart = b.x;
    float bEnd   = b.x + b.y;

    if (a.y < 0.0 || b.y < 0.0)
        return a;

    if (bEnd <= aStart || bStart >= aEnd)
        return a;

    if (bStart <= aStart && bEnd >= aEnd)
        return vec2(0.0, -1.0);

    if (bStart <= aStart && bEnd < aEnd)
        return vec2(bEnd, aEnd - bEnd);

    if (bStart > aStart && bEnd >= aEnd)
        return vec2(aStart, bStart - aStart);

    if (bStart > aStart && bEnd < aEnd)
        return vec2(aStart, bStart - aStart);

    return vec2(0.0, -1.0);
}

float sdfEngine(vec3 cylinder1_base, vec3 cylinder2_base, vec3 p)
{
  if (abs(p.z) <= WIDTH2) {
    if (abs(p.y) <= NOZZLE)
      return p.y > 0.0 ? 0.15 - length(p.xy - cylinder1_base.xy) : 0.15 - length(p.xy - cylinder2_base.xy);
    else {
      vec2 o = vec2(min(p.x - (cylinder1_base.x - 0.15), p.x), NOZZLE);
      return length(o - vec2(p.x, abs(p.y)));
    };
  } else
    return abs(p.z) - WIDTH2;
}

float sdfRectangle(vec2 p, vec2 size)
{
  vec2 d = abs(p) - size;
  float result = min(max(d.x, d.y), 0.0);
  return result + length(max(d, 0.0));
}

bool insideBox(vec3 point, vec3 box_min, vec3 box_max)
{
  return all(greaterThanEqual(point, box_min)) && all(lessThanEqual(point, box_max));
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
    // float c = 0.4;
    // float log_c = log(c);
    // float start = log((limit - NOZZLE) / limit) / log_c;
    // return limit - limit * pow(c, start + x);
    return limit + (NOZZLE - limit) * pow(0.4, x + 0.2);
  } else {
    float bulge = NOZZLE - limit;
    float omega = OMEGA_FACTOR * bulge;
    float bumps = bulge * abs(sin(x * omega));
    return limit + bumps;
  };
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float aspect = iResolution.x / iResolution.y;
  vec2 uv = (fragCoord.xy / iResolution.xy * 2.0 - 1.0) * vec2(aspect, 1.0);
  vec2 mouse = iMouse.xy / iResolution.xy;
  mat3 rotation = rotation_z(0.1 * M_PI) * rotation_y((2.0 * mouse.x + 1.0) * M_PI);
  vec3 light = rotation * normalize(vec3(1.0, 1.0, 0.0));
  vec3 origin = rotation * vec3(0.0, 0.0, -DIST);
  vec3 direction = normalize(rotation * vec3(uv, F));
  vec3 engine_min = vec3(START, -NOZZLE, -WIDTH2);
  vec3 engine_max = vec3(START + 0.22, NOZZLE, WIDTH2);
  float pressure = pressure();
  float box_size = max(limit(pressure), NOZZLE);
  vec3 normal;
  vec2 box = ray_box(vec3(START, -box_size, -WIDTH2), vec3(END, box_size, WIDTH2), origin, direction, normal);
  vec2 engine = ray_box(engine_min, engine_max, origin, direction, normal);
  vec3 normal1;
  vec3 normal2;
  vec3 cylinder1_base = vec3(START + 0.22, 0.22, -WIDTH2);
  vec3 cylinder1_axis = vec3(0.0, 0.0, 2.0 * WIDTH2);
  vec3 cylinder2_base = vec3(START + 0.22, -0.22, -WIDTH2);
  vec3 cylinder2_axis = vec3(0.0, 0.0, 2.0 * WIDTH2);
  vec2 cylinder1 = intersectCylinder(origin, direction, cylinder1_base, cylinder1_axis, 0.2, normal1);
  vec2 cylinder2 = intersectCylinder(origin, direction, cylinder2_base, cylinder2_axis, 0.2, normal2);
  vec2 joint = subtractInterval(subtractInterval(engine, cylinder1), cylinder2);
  vec3 color = vec3(0, 0, 0);
  if (joint.x == cylinder1.x + cylinder1.y)
    normal = normal1;
  if (joint.x == cylinder2.x + cylinder2.y)
    normal = normal2;
  if (joint.y > 0.0) {
    if (dot(normal, direction) > 0.0)
      normal = -normal;
    float diffuse = clamp(dot(normal, light), 0.0, 1.0);
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
      float height = bumps(p.x - engine_max.x);
      if (p.x >= engine_min.x) {
        float engine_pos = clamp((p.x - engine_max.x + 0.2) / 0.2, 0.0, 1.0);
        float transition = (limit(pressure) - SCALING) / (NOZZLE - SCALING);
        engine_pos = 1.0 - (1.0 - engine_pos) * clamp(1.0 - transition, 0.0, 1.0);
        if (mix(sdfEngine(cylinder1_base, cylinder2_base, p), sdfRectangle(p.yz, vec2(height, WIDTH2)), engine_pos) < 0.0) {
          float density = 2.0;
          color = color * pow(0.2, ds * density);
          vec3 flame_color = vec3(1.0, 0.0, 0.0);
          color += flame_color * ds * density;
        };
      };
    };
  };
  fragColor = vec4(color, 1.0);
}
