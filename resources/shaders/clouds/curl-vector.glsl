#version 450 core

vec3 <%= gradient-name %>(vec3 point);
vec3 rotate_vector(vec3 axis, vec3 v, float cos_angle, float sin_angle);
vec3 project_vector(vec3 n, vec3 v);

// note that point needs to be a unit vector
vec3 <%= method-name %>(vec3 point)
{
  vec3 grad = <%= gradient-name %>(point);
  vec3 tangential = grad - project_vector(point, grad);
  return rotate_vector(point, tangential, 0, 1);
}
