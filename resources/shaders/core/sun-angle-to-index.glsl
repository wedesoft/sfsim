#version 450 core

float sun_angle_to_index(vec3 direction, vec3 light_direction)
{
  return 0.5 * (1 + dot(direction, light_direction));
}
