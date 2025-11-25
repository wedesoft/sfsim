#version 450 core

// Determine intersection of ray with sphere (returns distance and length of intersection).
vec2 <%= method-name %>(<%= vector-type %> centre, float radius, <%= vector-type %> origin, <%= vector-type %> direction)
{
  <%= vector-type %> pos = origin - centre;
  float direction_sqr = dot(direction, direction);
  float direction_pos = dot(direction, pos);
  float discriminant = direction_pos * direction_pos - direction_sqr * (dot(pos, pos) - radius * radius);
  if (discriminant > 0) {
    float half_length = sqrt(discriminant) / direction_sqr;
    float middle = -dot(direction, pos) / direction_sqr;
    vec2 result = vec2(middle - half_length, 2 * half_length);
    if (result.x < 0) {
      result.y = max(0, result.y + result.x);
      result.x = 0;
    };
    return result;
  } else
    return vec2(0, 0);
}
