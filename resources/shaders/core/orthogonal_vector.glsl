#version 410 core

// Given a normal vector return an orthogonal vector.
vec3 orthogonal_vector(vec3 n)
{
  vec3 v;
  if (abs(n.x) <= min(abs(n.y), abs(n.z))) v = vec3(1, 0, 0);
  if (abs(n.y) <= min(abs(n.x), abs(n.z))) v = vec3(0, 1, 0);
  if (abs(n.z) <= min(abs(n.x), abs(n.y))) v = vec3(0, 0, 1);
  return normalize(cross(n, v));
}
