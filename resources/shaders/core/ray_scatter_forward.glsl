#version 410 core

float height_to_index(vec3 point);
float elevation_to_index(vec3 point, vec3 direction, bool above_horizon);
float sun_elevation_to_index(vec3 point, vec3 light_direction);
float sun_angle_to_index(vec3 direction, vec3 light_direction);

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, bool above_horizon)
{
  float height_index = height_to_index(point);
  float elevation_index = elevation_to_index(point, direction, above_horizon);
  float sun_elevation_index = sun_elevation_to_index(point, light_direction);
  float sun_angle_index = sun_angle_to_index(direction, light_direction);
  return vec4(sun_angle_index, sun_elevation_index, elevation_index, height_index);
}
