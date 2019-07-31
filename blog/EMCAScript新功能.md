title: ECMAScript新功能
date: 2016-12-09 23:39:09
categories: JavaScript Blog
tags: [JavaScript]
---

1. **let 为块作用域**

	eg.

	``` javascript
	if(true){
	    let apple = 'apple';
	}
	console.log(apple);
	```

	以上代码会报错： apple 没有定义

2. **const 恒量，一旦声明就不能再改变它的值**

	eg.

	``` javascript
	const apple = 'apple';
	apple = 'orange';
	```

	以上代码会报错： apple 只能是 read-only

3. **解构数组**

	eg.

	``` javascript
	function breakfast(){
		return ['cake', 'cookie', 'milk'];
	}
	let [tmp1, tmp2, tmp3] = breakfast();
	console.log(tmp1, tmp2, tmp3);
	```

	运行结果：cake，cookie，milk

4. **解构对象**

	eg.

	``` javascript
	function breakfast(){
		return {cake:'cake', cookie:'cookie', milk:'milk'};
	}
	let {tmp1:cake, tmp2:cookie, tmp3:milk} = breakfast();
	console.log(tmp1, tmp2, tmp3);
	```

	运行结果：cake，cookie，milk

5. **模版字符串**

	eg.

	``` javascript
	let cake = 'cake',
		coffee = 'coffee';

	let temp = `I want drink ${coffee} and eat ${cake}`;

	console.log(temp);
	```

	运行结果：I want drink coffee and eat cake

6. **带标签的模版字符串**

	eg.

	``` javascript
	let cake = 'cake',
		coffee = 'coffee';

	let temp = kitchen`I want drink ${coffee} and eat ${cake}`;

	function kitchen(strings, ...values){
		console.log(strings);
		console.log(values);
	}
	```

	运行结果：[I want drink , and eat ]
			 [cake,coffee]

7. **判断字符串里是否包含其他字符串**

	eg.

	``` javascript
	let cake = 'cake',
		coffee = 'coffee';

	console.log(cake.startsWith('ca'));
	console.log(coffee.endsWith('ee'));
	console.log(coffee.includes('off'));
	```

	运行结果：true, true, true

8. **默认参数**

	eg.

	``` javascript
	function breakfast(cake='cake', coffee='coffee'){
		return `${cake} ${coffee}`
	}

	console.log(breakfast());
	```

	运行结果：cake coffee

9. **展开操作符-Spread**

	eg.

	``` javascript
	let breakfast = ['cake', 'coffee'];
	let breakfast2 = ['milk', ...breakfast];
	console.log(breakfast);
	console.log(...breakfast);
	```

	运行结果：['cake', 'coffee']
			 cake coffee
			 milk cake coffee

10. **剩余操作符-Rest**

	eg.

	``` javascript
	function breakfast(cake, coffee, ...other){
		console.log(cake, coffee, other);
	}

	breakfast('cake', 'coffee', 'milk', 'candy');
	```
	
	运行结果：cake coffee ['milk', 'candy']

11. **解构参数**

	eg.

	``` javascript
	function breakfast(cake, coffee, {location, restaurant}={}){
		console.log(cake, coffee, location, restaurant);
	}

	breakfast('cake', 'coffee', {location:'hangzhou', restaurant:'xinbailu');
	```

	运行结果：cake coffee hangzhou xinbailu

12. **函数的名字-name属性**

	eg.

	``` javascript
	function breakfast(cake, coffee, {location, restaurant}={}){
		
	}
	console.log(breakfast.name);
	```

	运行结果：breakfast

13. **箭头函数-Arrow Fuctions**

	eg.

	``` javascript
	let breakfast = coffee => coffee;
	```

	即

	``` javascript
	let breakfast = function breakfast(coffee){
		return coffee;
	} 
	```

	=> 的左边是函数的参数，右边是返回值，若无参数则可以写成

	``` javascript
	let breakfast = () => 'coffee';
	```

	若没有返回值，则可以写成

	``` javascript
	let breakfast = coffee => {
		console.log(coffee);
	}
	```

14. **对象表达式**

	eg.

	``` javascript
	let coffee = 'coffee',
		cake = 'cake';

	let food = {
		coffee,
		cake
	}
	```

	在 food 对象中的 coffee 和 cake 直接引用的就是上面的 coffee 和 cake

15. **对象属性名**

	eg.

	``` javascript
	let coffee = {}；
	coffee.color = 'brown';
	console.log(coffee);
	```

	运行结果：Object {color:"brown"}

16. **对比两个值是否相等-Object.is()**

	eg.

	``` javascript
	console.log(Nan == Nan);
	console.log(Object.is(Nan, Nan));
	```

	运行结果：false，true

17. **把对象的值复制到另一个对象里 - Object.assign()**

	eg.

	``` javascript
	let breakfast = {};
	Object.assign(breakfast,{coffee:'coffee'});
	console.log(breakfast);
	```

	运行结果：Object {coffee:'coffee'}

18. **设置对象的 prototype - Object.setPrototypeOf()**

	eg.

	``` javascript
	let breakfast = {
		getDrink(){
			return 'coffee';
		}
	}

	let dinner = {
		getDrink(){
			return 'milk';
		}
	}

	let tmp1 = Object.create(breakfast);
	console.log(tmp1.getDrink());
	Object.setPrototypeOf(tmp1, dinner);
	console.log(tmp1.getDrink());
	```
	
	`Object.setPrototypeOf()` 第一个参数为要设置的对象，第二个参数为对应的属性或方法
	
	运行结果：coffee, milk

19. **__proto__**

	eg.

	``` javascript
	let breakfast = {
		getDrink(){
			return 'coffee';
		}
	}

	let dinner = {
		getDrink(){
			return 'milk';
		}
	}

	let tmp1 = {
		__proto__ = breakfast;
	}
	console.log(tmp1.getDrink());
	tmp1.__proto__ = dinner;
	console.log(tmp1.getDrink());
	```

	运行结果：coffee, milk

20. **super**

	eg.

	``` javascript
	let breakfast = {
		getDrink(){
			return 'coffee';
		}
	}

	let tmp1 = {
		__proto__ = breakfast,
		getDrink(){
			return super.getDrink() + 'milk';
		}
	}
	console.log(tmp1.getDrink());
	```

	运行结果：coffeemilk

21. **迭代器 - Iterators**

	eg.

	``` javascript
	function cook(foods){
		let i = 0;
		return{
			next(){
				let done = i >=foods.length;
				let value = done?undefined:foods[i++];
				return {
					done:done,
					value:value
				}
			}
		}
	}

	let chaohao = cook(['apple', 'banana']);

	console.log(chaohao.next());
	console.log(chaohao.next());
	console.log(chaohao.next());
	```
	
	Iterators 中有两个属性，value 代表着当前迭代的值，done 表示迭代是否完成了。

	运行结果：Object{value:'apple', done:false}
			Object{value:'banana', done:false}
			Object{value:'undefined', done:true}

22. **生成器 - Generators**

	eg.

	``` javascript
	function* chef(){
		yield 'coffee';
		yield 'milk';
	}

	let tmp = chef();
	console.log(tmp.next());
	console.log(tmp.next());
	```

	Generators 表达式就是 function* xxx(){...}

23. **Classes - 类**

	eg.

	``` javascript
	class Chef {
		constructor(food){
			this.food = food;
		}
		cook(){
			console.log(this.food);
		}
	}

	let tmp = new Chef('coffee');
	tmp.cook();
	```

24. **get 与 set**

	eg.

	``` javascript
	class Chef {
		constructor(food, dish){
			this.food = food;
			this.dish = [];
		}

		get menu(){
			return this.dish;
		}

		set menu(dish){
			this.dish.push(dish);
		}

		cook(){
			console.log(this.food);
		}
	}

	let tmp = new Chef('coffee');
	tmp.menu = 'milk';
	tmp.menu = 'juice';
	console.log(tmp.menu);
	```

	运行结果：milk,juice

25. **静态方法-static**

	eg.

	``` javascript
	class Chef {
		constructor(food, dish){
			this.food = food;
			this.dish = [];
		}

		static cook(food){
			console.log(this.food);
		}
	}

	console.log(Chef.cook('coffee'));
	```

26. **继承-extends**

	eg.

	``` javascript
	class Chef {
		constructor(food, dish){
			this.food = food;
			this.dish = dish;
		}

		cook(){
			return `${food}，${dish}`;
		}
	}

	class Chef2 extends Chef {
		constructor(food, dish){
			super(food,dish);
		}
	}

	let tmp = new Chef2('coffee','milk');
	console.log(Chef2.cook());
	```	
	
	运行结果：coffee，milk

27. **Set**

	eg.

	``` javascript
	let desserts = new Set('cake','cookie');
	desserts.size(); // 一共有多少节点
	desserts.add('milk'); // 添加节点
	desserts.has('cake'); // 判断节点是否被包含
	desserts.delete('cookie'); // 删除节点
	desserts.forEach(dessert => {
		console.log(dessert); // 遍历节点
	})
	desserts.clear(); // 清空节点
	```

28. **Map**

	eg.

	``` javascript
	let desserts = new Map();
	desserts.set('drink','milk'); // 添加节点
	desserts.set('food','cake');
	desserts.size(); // 一共有多少节点
	desserts.has('cake'); // 判断节点是否被包含
	desserts.delete('cookie'); // 删除节点
	desserts.forEach((key,value) => {
		console.log(`${key} = ${value}`); // 遍历节点
	})
	desserts.clear(); // 清空节点
	```

29. **Module**

	eg.

	chef.js

	``` javascript
	export let fruit = 'apple';
	export let drink = 'coffee';
	```

	index.js

	``` javascript
	import {fruit, drink} from './modules/chef';
	console.log(fruit, drink);
	```

30. `重命名导出与导入的东西`

	eg.

	chef.js

	``` javascript
	let fruit = 'apple';
	let drink = 'coffee';
	export {fruit, drink as water}
	```

	index.js

	``` javascript
	import {fruit, water as drink} from './modules/chef';
	console.log(fruit, drink);
	```

31. **导出与导入默认**

	eg.

	chef.js

	``` javascript
	let fruit = 'apple';
	let drink = 'coffee';
	export {fruit, drink as default}
	```

	index.js

	``` javascript
	import drink, {fruit} from './modules/chef';
	console.log(fruit, drink);
	```

	每个模块默认导出的只有一个，即只有一个 export default ，但是 export 导出的可以有多个。