#version 450 core

#define M_PI 3.1415926535897932384626433832795

uniform float nozzle;
uniform float diamond_strength;
uniform float min_limit;

float plume_limit(float pressure);
float plume_omega(float limit);
float diamond_phase(float x, float limit);

float diamond(float pressure, vec2 uv)
{
  float limit = plume_limit(pressure);
  if (nozzle > limit) {
    float bulge = nozzle - limit;
    float omega = plume_omega(limit);
    float diamond_longitudinal = diamond_phase(uv.x, limit);
    float diamond_front_length = limit / bulge;
    float diamond_back_length = diamond_front_length * 0.3;
    float diamond_radius = limit * min(1.0 - diamond_longitudinal / diamond_back_length, 1.0 + diamond_longitudinal / diamond_front_length);
    float ramp = max(0.0, min(1.0 - diamond_longitudinal / (0.3 * M_PI), 1.0 + diamond_longitudinal / (0.7 * M_PI)));
    float decay = (nozzle - limit) / (nozzle - min_limit);
    return diamond_strength * ramp * decay * (1.0 - smoothstep(diamond_radius - <%= fringe %>, diamond_radius, abs(uv.y)));
  } else
    return 0.0;
}
