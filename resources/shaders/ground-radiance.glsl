#version 410 core

float M_PI = 3.14159265358;

vec3 ground_radiance(float albedo, vec3 color)
{
  return (albedo / M_PI) * color;
}
