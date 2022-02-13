#version 410 core

vec3 orthogonal_vector(vec3 n);

mat3 oriented_matrix(vec3 n) {
  vec3 o1 = orthogonal_vector(n);
  vec3 o2 = cross(n, o1);
  return transpose(mat3(n, o1, o2));
}
