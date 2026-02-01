#version 450 core

// Determine intersection of ray with an axis-aligned box (returns distance and length of intersection).
// https://gist.github.com/DomNomNom/46bb1ce47f68d255fd5d


vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  vec3 inv_dir = 1.0 / direction;
  vec3 smin = (box_min - origin) * inv_dir;
  vec3 smax = (box_max - origin) * inv_dir;
  vec3 s1 = min(smin, smax);
  vec3 s2 = max(smin, smax);
  float s_near = max(max(max(s1.x, s1.y), s1.z), 0.0);
  float s_far = max(min(min(s2.x, s2.y), s2.z), 0.0);
  if (isinf(s_near) || isinf(s_far))
    return vec2(0.0, 0.0);
  else
    return vec2(s_near, s_far - s_near);
}
