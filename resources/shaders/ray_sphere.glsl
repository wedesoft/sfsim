vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction) {
  vec3 offset = origin - centre;
  float direction_sqr = dot(direction, direction);
  float discriminant = pow(dot(direction, offset), 2) - direction_sqr * (dot(offset, offset) - radius * radius);
  if (discriminant > 0) {
    float half_length = sqrt(discriminant) / direction_sqr;
    float middle = -dot(direction, offset) / direction_sqr;
    vec2 result = vec2(middle - half_length, 2 * half_length);
    if (result.x < 0) {
      result.y = max(0, result.y + result.x);
      result.x = 0;
    };
    return result;
  } else {
    return vec2(0, 0);
  }
}
