#version 410 core

float height_to_index(vec3 point);
float elevation_to_index(vec3 point, vec3 direction, bool above_horizon);

vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon)
{
  float height_index = height_to_index(point);
  float elevation_index = elevation_to_index(point, direction, above_horizon);
  return vec2(elevation_index, height_index);
}
