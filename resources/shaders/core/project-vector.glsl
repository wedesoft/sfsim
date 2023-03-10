#version 410 core

vec3 project_vector(vec3 n, vec3 x)
{
  return (dot(n, x) / dot(n, n)) * n;
}
