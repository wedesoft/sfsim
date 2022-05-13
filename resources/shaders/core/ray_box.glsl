#version 410 core

// Determine intersection of ray with an axis-aligned box (returns distance and length of intersection).
// https://gist.github.com/DomNomNom/46bb1ce47f68d255fd5d
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  vec3 factors1 = (box_min - origin) / direction;
  vec3 factors2 = (box_max - origin) / direction;
  vec3 intersections1 = min(factors1, factors2);
  vec3 intersections2 = max(factors1, factors2);
  float near = max(max(max(intersections1.x, intersections1.y), intersections1.z), 0);
  float far = min(min(intersections2.x, intersections2.y), intersections2.z);
  return vec2(near, max(far - near, 0));
}
