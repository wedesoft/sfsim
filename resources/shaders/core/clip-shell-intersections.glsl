#version 410 core

vec4 clip_shell_intersections(vec4 intersections, float limit)
{
  vec4 result;
  if (limit > intersections.x) {
    result.x = intersections.x;
    result.y = min(intersections.y, limit - intersections.x);
  } else {
    result.x = 0;
    result.y = 0;
  };
  if (limit > intersections.z) {
    result.z = intersections.z;
    result.w = min(intersections.w, limit - intersections.z);
  } else {
    result.z = 0;
    result.w = 0;
  };
  return result;
}
