#version 410 core


float sdf_box(vec3 point, vec3 box_min, vec3 box_max)
{
  vec3 q = max(box_min - point, point - box_max);
  return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}
