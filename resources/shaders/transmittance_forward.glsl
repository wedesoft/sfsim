#version 410 core

vec2 transmittance_forward(vec3 point, vec3 direction, float radius, int size, float power)
{
  return vec2(0.5, 0.5) / size;
}
