A=zeros(3,4);
B=ones(3,4);

for ii=1:3;
A=A.^2+B.^2;
end;
ii=0;
A=A+B
if ii==0;
A(ii)=0;
end;
while ii<3
ii=ii+1;
end;
