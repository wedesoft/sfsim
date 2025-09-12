#version 410 core

#define M_PI 3.1415926535897932384626433832795
#define nozzle 0.2
#define scaling 0.1
#define max_cone 1.5

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

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
  return scaling * sqrt(1.0 / pressure);
}

float bumps(float x)
{
  float pressure = pressure();
  float limit = limit(pressure);
  if (nozzle < limit) {
    float equal_pressure = sqr(scaling / nozzle);
    float factor = max_cone * (equal_pressure - pressure) / equal_pressure;
    float derivative = factor * 2.0 * iResolution.x / iResolution.y;
    float c = exp(-derivative / limit);
    float start = log((limit - nozzle) / limit) / log(c);
    return limit - limit * pow(c, start + x);
  } else {
    float bulge = nozzle - limit;
    float omega = 100.0 * bulge;
    float bumps = bulge * abs(cos(x * omega));
    return limit + bumps;
  };
}

float diamond(vec2 uv)
{
  float pressure = pressure();
  float limit = limit(pressure);
  float diamond;
  if (nozzle > limit) {
    float bulge = nozzle - limit;
    float omega = 100.0 * bulge;
    float phase = omega * uv.x + M_PI / 2;
    float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
    float diamond_front_length = limit / (bulge * omega);
    float diamond_back_length = diamond_front_length * 0.7;
    float tail_start = 0.3 * diamond_front_length;
    float tail_length = 0.8 * diamond_front_length;
    float diamond_length = diamond_longitudinal > 0.0 ? diamond_back_length : diamond_front_length;
    float diamond_radius = limit * max(0.0, 1.0 - abs(diamond_longitudinal / omega) / diamond_length);
    diamond = abs(uv.y) <= diamond_radius ? 1.0 : 0.5;
  } else {
    diamond = 0.5;
  };
  return diamond;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float fov = 2.0;
  vec2 uv = vec2(fragCoord.x / iResolution.x, 2.0 * fragCoord.y / iResolution.y - 1.0) * fov;
  float radius = bumps(uv.x);
  bool inside = abs(uv.y) <= radius;
  vec3 color;
  if (inside) {
    float a = radius * radius;
    float d = sqrt(radius * radius - uv.y * uv.y);
    float diamond = diamond(uv);
    color = 0.1 * diamond * vec3(1, 1, 1) * d / a;
  } else {
    color = vec3(0, 0, 0);
  };
  fragColor =  vec4(color, 1);
}
