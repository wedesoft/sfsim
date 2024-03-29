#version 410 core

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);

vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction)
{
  vec2 outer_intersection = ray_sphere(centre, outer_radius, origin, direction);
  vec2 inner_intersection = ray_sphere(centre, inner_radius, origin, direction);
  if (inner_intersection.t > 0) {
    float a = outer_intersection.s;
    float b = inner_intersection.s;
    float c = inner_intersection.s + inner_intersection.t;
    float d = outer_intersection.s + outer_intersection.t;
    if (b > a)
      return vec4(a, b - a, c, d - c);
    else
      return vec4(c, d - c, 0, 0);
  } else
    return vec4(outer_intersection.s, outer_intersection.t, 0, 0);
}
