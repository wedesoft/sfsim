#version 450 core


float sdf_circle(vec2 point, vec2 center, float radius)
{
  return length(point - center) - radius;
}
