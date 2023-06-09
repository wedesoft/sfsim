#version 410 core

vec3 polar_stretch(vec3 vector, float radius, float polar_radius)
{
  return vec3(vector.x / radius, vector.y / radius, vector.z / polar_radius);
}
