#version 410 core

vec3 polar_stretch(vec3 vector, float radius, float polar_radius);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);

// Determine intersection of ray with ellipsoid (returns distance and length of intersection).
vec2 ray_ellipsoid(vec3 centre, float radius, float polar_radius, vec3 origin, vec3 direction)
{
  vec3 scaled_centre = polar_stretch(centre, radius, polar_radius);
  vec3 scaled_origin = polar_stretch(origin, radius, polar_radius);
  vec3 scaled_direction = polar_stretch(direction, radius, polar_radius);
  return ray_sphere(scaled_centre, radius, scaled_origin, scaled_direction);
}
