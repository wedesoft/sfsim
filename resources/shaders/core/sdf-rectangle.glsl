#version 410 core


float sdf_rectangle(vec2 point, vec2 rectangle_min, vec2 rectangle_max)
{
  vec2 q = max(rectangle_min - point, point - rectangle_max);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
}
