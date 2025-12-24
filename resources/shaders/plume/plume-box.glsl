#version 450 core

vec3 plume_box_size();
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);

vec2 plume_box(vec3 origin, vec3 direction)
{
  vec3 box = plume_box_size();
  return ray_box(vec3(box.y, -box.x, -box.x), vec3(box.z, box.x, box.x), origin, direction);
}
