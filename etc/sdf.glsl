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
#define MAX_CONE 0.5

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
  if (a.y < 0.0 || b.y < 0.0 || b.x + b.y <= a.x || b.x >= a.x + a.y)
    return a;

  if (b.x <= a.x && b.x + b.y >= a.x + a.y)
    return vec2(0.0, -1.0);

  if (b.x <= a.x)
    return vec2(b.x + b.y, a.x + a.y - (b.x + b.y));

  return vec2(a.x, b.x - a.x);
}

float sdfEngine(vec3 cylinder1_base, vec3 cylinder2_base, vec3 p) {
  if (abs(p.z) > WIDTH2) {
    return abs(p.z) - WIDTH2;
  }
  if (abs(p.y) <= NOZZLE) {
    vec2 base = p.y > 0.0 ? cylinder1_base.xy : cylinder2_base.xy;
    return 0.15 - length(p.xy - base);
  }
  vec2 o = vec2(min(p.x - (cylinder1_base.x - 0.15), p.x), NOZZLE);
  return length(o - vec2(p.x, abs(p.y)));
}

float sdfRectangle(vec2 p, vec2 size)
{
  vec2 d = abs(p) - size;
  float result = min(max(d.x, d.y), 0.0);
  return result + length(max(d, 0.0));
}

float sdfCircle(vec2 p, float radius)
{
  return length(p) - radius;
}

bool intersectRaySphere(vec3 rayOrigin, vec3 rayDir, vec3 sphereCenter, float sphereRadius, out float t) {
  vec3 oc = rayOrigin - sphereCenter;
  float a = dot(rayDir, rayDir);
  float b = 2.0 * dot(oc, rayDir);
  float c = dot(oc, oc) - sphereRadius * sphereRadius;
  float discriminant = b * b - 4.0 * a * c;

  if (discriminant < 0.0) {
    return false;
  } else {
    t = (-b - sqrt(discriminant)) / (2.0 * a);
    return true;
  }
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

vec2 envelope(float pressure, float x) {
  float limit = limit(pressure);
  float bulge = NOZZLE - limit;
  if (NOZZLE < limit) {
    float equilibrium = SCALING * SCALING / (NOZZLE * NOZZLE);
    float c = exp(-MAX_CONE * (equilibrium - pressure) / (equilibrium * limit));
    float decay = pow(c, x + 0.2);
    return vec2(limit + WIDTH2 - NOZZLE + bulge * decay, limit + bulge * decay);
  } else {
    float bumps = bulge * abs(sin(x * OMEGA_FACTOR * bulge));
    return vec2(limit + bumps + WIDTH2 - NOZZLE, limit + bumps);
  }
}

float diamond(float pressure, vec2 uv)
{
  float limit = limit(pressure);
  float diamond;
  if (NOZZLE > limit) {
    float bulge = NOZZLE - limit;
    float omega = OMEGA_FACTOR * bulge;
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
  // float ry = iMouse.x / iResolution.x;
  float ry = 0.3;
  mat3 rotation = rotation_z(0.1 * M_PI) * rotation_y((2.0 * ry + 1.0) * M_PI);
  vec3 light = rotation * normalize(vec3(1.0, 1.0, 0.0));
  vec3 origin = rotation * vec3(0.0, 0.0, -DIST);
  vec3 direction = normalize(rotation * vec3(uv, F));
  vec3 engine_min = vec3(START, -NOZZLE, -WIDTH2);
  vec3 engine_max = vec3(START + 0.22, NOZZLE, WIDTH2);
  // float pressure = pressure();
  float pressure = 1.0;
  float box_size = max(limit(pressure), NOZZLE) + WIDTH2 - NOZZLE;
  vec3 normal;
  vec2 box = ray_box(vec3(START, -box_size, -box_size), vec3(END, box_size, box_size), origin, direction, normal);
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
      if (p.x >= engine_min.x) {
        vec2 envelope = envelope(pressure, p.x - engine_max.x);
        float engine_pos = clamp((p.x - engine_max.x + 0.2) / 0.2, 0.0, 1.0);
        float transition = clamp((limit(pressure) - SCALING) / (NOZZLE - SCALING), 0.0, 1.0);
        float circular = clamp((p.x - engine_max.x) / (END - engine_max.x), 0.0, 1.0);
        float radius = 0.5 * (envelope.x + envelope.y);
        engine_pos = clamp(engine_pos + transition, 0.0, 1.0);
        float slider1 = iMouse.x / iResolution.x;
        float slider2 = iMouse.y / iResolution.y;
        float distortion1 = max(0.0, 5.0 * p.y * p.z * (slider1 - 0.5));
        float distortion2 = max(0.0, 5.0 * p.y * (slider2 - 0.5));
        float baseSdf = sdfEngine(cylinder1_base, cylinder2_base, p);
        float shapeMix = mix(sdfRectangle(p.zy, envelope), sdfCircle(p.zy, radius), circular);
        float sdf = mix(baseSdf, shapeMix, engine_pos) + distortion1 + distortion2;
        if (sdf < 0.0) {
          float dz = mix(WIDTH2, envelope.x, engine_pos);
          float dy = mix(0.2 - 0.15, envelope.y, engine_pos);
          float density = 0.2 / (dz * dy) * (1.0 - circular);
          float fringe = max(1.0 + sdf / 0.1, 0.0);
          vec3 scale = 20.0 * vec3(0.1, NOZZLE / envelope.y, NOZZLE / envelope.x);
          float attenuation = 0.7 + 0.3 * noise(p * scale + iTime * vec3(-SPEED, 0.0, 0.0));
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
