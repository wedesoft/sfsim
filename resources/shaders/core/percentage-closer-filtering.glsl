#version 410 core

uniform int <%= shadow-size %>;

float <%= base-function-name %>(<%= (apply str (mapv #(str (% 0) " " (% 1) ", ") parameters)) %>vec4 point);

float <%= method-name %>(<%= (apply str (mapv #(str (% 0) " " (% 1) ", ") parameters)) %>vec4 point)
{
  return <%= base-function-name %>(<%= (apply str (mapv #(str (% 1) ", ") parameters)) %>point);
  // float texel_size = 1.0 / (<%= shadow-size %> - 1);
  // float result = 0.0;
  // for (int y=-1; y<=1; y++)
  //   for (int x=-1; x<=1; x++) {
  //     vec4 sampling_point = point + vec4(x * texel_size, y * texel_size, 0, 0);
  //     result += <%= base-function-name %>(<%= (apply str (mapv #(str (% 1) ", ") parameters)) %>sampling_point);
  //   };
  // return result / 9.0;
}
