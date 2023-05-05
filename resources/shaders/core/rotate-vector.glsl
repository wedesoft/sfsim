#version 410 core

mat3 oriented_matrix(vec3 n);

// note that axis needs to be a unit vector
vec3 rotate_vector(vec3 axis, vec3 v, float cos_angle, float sin_angle)
{
  mat3 orientation = oriented_matrix(axis);
  vec3 oriented = orientation * v;
  mat2 rotation = mat2(cos_angle, sin_angle, -sin_angle, cos_angle);
  vec3 rotated = vec3(oriented.x, rotation * oriented.yz);
  return transpose(orientation) * rotated;
}
