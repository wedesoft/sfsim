#version 410 core

uniform int shadow_size;

float <%= base-function-name %>(<%= (apply str (map #(str (% 0) " " (% 1) ", ") parameters)) %><%= point-type %> point);

float <%= method-name %>(<%= (apply str (map #(str (% 0) " " (% 1) ", ") parameters)) %><%= point-type %> point)
{
  float texel_size = 1.0 / (shadow_size - 1);
  float result = 0.0;
  point.x -= texel_size;
  point.y -= texel_size;
  for (int y=-1; y<=1; y++) {
    for (int x=-1; x<=1; x++) {
      result += <%= base-function-name %>(<%= (apply str (map #(str (% 1) ", ") parameters)) %>point);
      point.x += texel_size;
    };
    point.x -= texel_size * 3;
    point.y += texel_size;
  };
  return result / 9.0;
}
