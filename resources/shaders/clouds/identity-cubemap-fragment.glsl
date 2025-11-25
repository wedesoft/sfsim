#version 450 core

uniform int size;

layout (location = 0) out vec3 output1;
layout (location = 1) out vec3 output2;
layout (location = 2) out vec3 output3;
layout (location = 3) out vec3 output4;
layout (location = 4) out vec3 output5;
layout (location = 5) out vec3 output6;

vec3 face1_vector(vec2 texcoord);
vec3 face2_vector(vec2 texcoord);
vec3 face3_vector(vec2 texcoord);
vec3 face4_vector(vec2 texcoord);
vec3 face5_vector(vec2 texcoord);
vec3 face6_vector(vec2 texcoord);

void main()
{
  vec2 x = (gl_FragCoord.xy - 0.5) / (size - 1);
  output1 = face1_vector(x);
  output2 = face2_vector(x);
  output3 = face3_vector(x);
  output4 = face4_vector(x);
  output5 = face5_vector(x);
  output6 = face6_vector(x);
}
