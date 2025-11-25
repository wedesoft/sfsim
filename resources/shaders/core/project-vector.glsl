#version 450 core

vec3 project_vector(vec3 n, vec3 v)
{
  return (dot(n, v) / dot(n, n)) * n;
}
