#version 410 core

uniform float radius;
uniform float max_height;
uniform int elevation_size;
uniform int height_size;

float horizon_angle(vec3 point);
float elevation_to_index(int elevation_size, float elevation, float horizon_angle, bool above_horizon);

// Convert input parameters to 2D index for lookup in transmittance table.
vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon)
{
  float dist = length(point);
  float height = dist - radius;
  vec3 normal = point / dist;
  float elevation = acos(dot(normal, direction));
  float horizon = horizon_angle(point);
  float elevation_index = elevation_to_index(elevation_size, elevation, horizon, above_horizon);
  return vec2(elevation_index, height / max_height);
}
