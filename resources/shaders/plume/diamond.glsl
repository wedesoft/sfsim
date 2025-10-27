#version 410 core

uniform float nozzle;
uniform float diamond_strength;

float limit(float pressure);
float plume_omega(float limit);
float diamond_phase(float x, float limit);

float diamond(float pressure, vec2 uv)
{
  float limit = limit(pressure);
  if (nozzle > limit) {
    float bulge = nozzle - limit;
    float omega = plume_omega(limit);
    float diamond_longitudinal = diamond_phase(uv.x, limit);
    float diamond_front_length = limit / (bulge * omega);
    float diamond_back_length = diamond_front_length * 0.3;
    float diamond_radius = nozzle * min(1.0 - diamond_longitudinal / diamond_back_length, 1.0 + diamond_longitudinal / diamond_front_length);
    return diamond_strength * (1.0 - smoothstep(diamond_radius - <%= fringe %>, diamond_radius, abs(uv.y)));
  } else
    return 0.0;
}
