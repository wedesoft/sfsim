#version 410 core

uniform int shadow_size;

float <%= base-function-name %>(<%= (apply str (map #(str (% 0) " " (% 1) ", ") parameters)) %><%= point-type %> point);

float <%= method-name %>(<%= (apply str (map #(str (% 0) " " (% 1) ", ") parameters)) %><%= point-type %> point)
{
  float texel_size = 1.0 / (shadow_size - 1);
  float result = 0.0;
  for (int y=-1; y<=1; y++)
    for (int x=-1; x<=1; x++) {
      <%= point-type %> sampling_point = point;
      sampling_point.xy += vec2(x * texel_size, y * texel_size);
      result += <%= base-function-name %>(<%= (apply str (map #(str (% 1) ", ") parameters)) %>sampling_point);
    };
  return result / 9.0;
}
