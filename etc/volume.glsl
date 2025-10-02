#version 410 core

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW
// 3D perlin noise: https://www.shadertoy.com/view/4djXzz

#define M_PI 3.1415926535897932384626433832795

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

#define M_PI 3.1415926535897932384626433832795
#define FOV (60.0 * M_PI / 180.0)
#define F (1.0 / tan(FOV / 2.0))
#define DIST 2.0
#define NOZZLE 0.2
#define SCALING 0.1
#define SAMPLES 100
#define OMEGA_FACTOR 75.0
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

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  vec3 factors1 = (box_min - origin) / direction;
  vec3 factors2 = (box_max - origin) / direction;
  vec3 intersections1 = min(factors1, factors2);
  vec3 intersections2 = max(factors1, factors2);
  float near = max(max(max(intersections1.x, intersections1.y), intersections1.z), 0);
  float far = min(min(intersections2.x, intersections2.y), intersections2.z);
  return vec2(near, max(far - near, 0));
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
    float omega = OMEGA_FACTOR * bulge;
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

float diamond(vec2 uv)
{
  float pressure = pressure();
  float limit = limit(pressure);
  float diamond;
  if (NOZZLE > limit) {
    float bulge = NOZZLE - limit;
    float omega = OMEGA_FACTOR * bulge;
    float phase = omega * uv.x + M_PI / 2.0;
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

// Ray - truncated cone (frustum) side intersection
// coneApex = center of bottom disk
// axisDir  = normalized axis direction (bottom -> top)
// height   = frustum height
// r0       = radius at bottom
// r1       = radius at top
//
// Returns true if hit, with intersection distance t and outward normal.
bool intersectConeSegment(
    vec3 rayOrigin,
    vec3 rayDir,          // normalized
    vec3 coneApex,        // bottom center
    vec3 axisDir,         // must be normalized
    float height,
    float r0,             // bottom radius
    float r1,             // top radius
    out float t,
    out vec3 normal
) {
    // Build parameters of equivalent infinite cone
    float dr = r1 - r0;
    float k  = dr / height;  // slope (change in radius per unit height)

    // Reference point: bottom center
    vec3 w  = rayOrigin - coneApex;
    float wv = dot(w, axisDir);
    float dv = dot(rayDir, axisDir);

    vec3 wPerp = w - wv * axisDir;
    vec3 dPerp = rayDir - dv * axisDir;

    // At height h, radius = r0 + k*h
    // Condition: |perp|^2 = ( (r0 + k*h)^2 / h^2 ) * (wv + t*dv)^2
    // Equivalent quadratic:
    float a = dot(dPerp, dPerp) - (k*k) * dv*dv;
    float b = 2.0 * (dot(wPerp, dPerp) - (k*k) * wv*dv - k*r0*dv);
    float c = dot(wPerp, wPerp) - (k*k) * wv*wv - 2.0*k*r0*wv - r0*r0;

    // Solve quadratic
    float disc = b*b - 4.0*a*c;
    if (disc < 0.0) return false;

    float s = sqrt(disc);
    float t0 = (-b - s) / (2.0*a);
    float t1 = (-b + s) / (2.0*a);
    float m0 = wv + t0*dv;
    float m1 = wv + t1*dv;

    // Pick nearest intersection in front
    float tCand = 1e30;
    if (t0 > 0.0 && t0 < tCand && m0 > 0.0 && m0 < height) tCand = t0;
    if (t1 > 0.0 && t1 < tCand && m1 > 0.0 && m1 < height) tCand = t1;
    if (tCand == 1e30) return false;

    // Intersection point
    vec3 P = rayOrigin + tCand * rayDir;
    vec3 v = P - coneApex;
    float hProj = dot(v, axisDir);

    // Normal = component outward from axis + slope
    vec3 vPerp = v - hProj*axisDir;
    // slope factor
    normal = normalize(normalize(vPerp) * height - (r1 - r0) * axisDir);

    t = tCand;
    return true;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float aspect = iResolution.x / iResolution.y;
  vec2 uv = (fragCoord.xy / iResolution.xy * 2.0 - 1.0) * vec2(aspect, 1.0);
  vec2 mouse = iMouse.xy / iResolution.xy;
  mat3 rotation = rotation_z(0.1 * M_PI) * rotation_y((mouse.x + 1.0) * M_PI);
  vec3 light = rotation * normalize(vec3(1.0, 1.0, 0.0));
  vec3 origin = rotation * vec3(0.0, 0.0, -DIST);
  vec3 direction = normalize(rotation * vec3(uv, F));
  float box_size = max(NOZZLE, limit(pressure()));
  vec2 box = ray_box(vec3(START, -box_size, -box_size), vec3(END, box_size, box_size), origin, direction);
  vec3 color = vec3(0, 0, 0);
  float t;
  vec3 normal;
  bool hit = intersectConeSegment(origin, direction, vec3(START - 0.2, 0, 0), vec3(1, 0, 0), 0.2, 0.3 * NOZZLE, NOZZLE, t, normal);
  if (hit && t > 0) {
    float diffuse = dot(normal, direction) < 0.0 ? clamp(dot(normal, light), 0.0, 1.0) : 0.0;
    color = vec3(1.0) * (diffuse * 0.9 + 0.1);
    if (box.x + box.y > t) {
      box.y = t - box.x;
    };
  };
  if (box.y > 0.0) {
    float ds = box.y / SAMPLES;
    for (int i = 0; i <= SAMPLES; i++)
    {
      float s = box.x + i * ds;
      vec3 p = origin + direction * s;
      float dist = length(p.yz);
      vec2 uv = vec2(p.x - START, dist);
      float radius = bumps(p.x - START);
      vec3 scale = 20.0 * vec3(0.1, NOZZLE / radius, NOZZLE / radius);
      float diamond = diamond(uv);
      float fringe = fringe(uv);
      vec3 flame_color = mix(vec3(0.90, 0.59, 0.80), vec3(0.50, 0.50, 1.00), fringe);
      float decay = 1.0 - uv.x / (END - START);
      float density = 2.0 * NOZZLE * NOZZLE / (radius * radius) * decay;
      if (dist <= radius) {
        float attenuation = 0.7 + 0.3 * noise(p * scale + iTime * vec3(-SPEED, 0.0, 0.0));
        color = color * pow(0.2, ds * density);
        color += flame_color * density * ds * attenuation;
        color += diamond * density * 10.0 * ds * vec3(1, 1, 1) * attenuation;
      }
    };
  };
  fragColor = vec4(color, 1.0);
}
