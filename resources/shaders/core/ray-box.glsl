#version 450 core

// Determine intersection of ray with an axis-aligned box (returns distance and length of intersection).
// https://gist.github.com/DomNomNom/46bb1ce47f68d255fd5d

bool inside(float a, float b, float origin)
{
  return a <= origin && origin <= b;
}

vec2 range(float a, float b, float origin, float direction)
{
  if (direction >= 0.0)
    return vec2(a - origin, b - origin) / direction;
  else
    return vec2(b - origin, a - origin) / direction;
}

vec2 intersect_range(vec2 range1, vec2 range2)
{
  return vec2(max(range1.x, range2.x), min(range1.y, range2.y));
}

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  bool result_valid = false;
  vec2 result = vec2(0, 0);
  if (direction.x == 0.0) {
    if (!inside(box_min.x, box_max.x, origin.x))
      return vec2(0, 0);
  } else {
    result = range(box_min.x, box_max.x, origin.x, direction.x);
    result_valid = true;
  };

  if (direction.y == 0.0) {
    if (!inside(box_min.y, box_max.y, origin.y))
      return vec2(0, 0);
  } else {
    if (result_valid)
      result = intersect_range(result, range(box_min.y, box_max.y, origin.y, direction.y));
    else {
      result = range(box_min.y, box_max.y, origin.y, direction.y);
      result_valid = true;
    };
  };

  if (direction.z == 0.0) {
    if (!inside(box_min.z, box_max.z, origin.z))
      return vec2(0, 0);
  } else {
    if (result_valid)
      result = intersect_range(result, range(box_min.z, box_max.z, origin.z, direction.z));
    else {
      result = range(box_min.z, box_max.z, origin.z, direction.z);
      result_valid = true;
    };
  };

  result.x = max(result.x, 0);
  return vec2(result.x, max(result.y - result.x, 0));
}
