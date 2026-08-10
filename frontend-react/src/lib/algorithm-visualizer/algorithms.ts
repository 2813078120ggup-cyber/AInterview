import type {
  AlgorithmStep,
  ArrayAlgorithmStep,
  GraphAlgorithmStep,
  GraphEdge,
  GraphNode,
  LinkedListNode,
  VisualizerAlgorithm,
} from './types'

const arrayStep = (step: Omit<ArrayAlgorithmStep, 'kind'>): ArrayAlgorithmStep => ({ ...step, kind: 'array' })

const graphNodes: GraphNode[] = [
  { id: 'A', label: 'A', x: 50, y: 14 },
  { id: 'B', label: 'B', x: 25, y: 43 },
  { id: 'C', label: 'C', x: 75, y: 43 },
  { id: 'D', label: 'D', x: 13, y: 76 },
  { id: 'E', label: 'E', x: 38, y: 76 },
  { id: 'F', label: 'F', x: 87, y: 76 },
]

const graphEdges: GraphEdge[] = [
  { from: 'A', to: 'B' },
  { from: 'A', to: 'C' },
  { from: 'B', to: 'D' },
  { from: 'B', to: 'E' },
  { from: 'C', to: 'F' },
]

const graphSnapshot = (step: Omit<GraphAlgorithmStep, 'kind' | 'nodes' | 'edges'>): GraphAlgorithmStep => ({
  ...step,
  kind: 'graph',
  nodes: graphNodes,
  edges: graphEdges,
})

function bubbleSort(input: number[]): AlgorithmStep[] {
  const values = [...input]
  const steps: AlgorithmStep[] = [arrayStep({ line: 1, type: 'start', data: [...values], description: '从数组左侧开始扫描相邻元素。' })]
  const sortedIndices: number[] = []

  for (let end = values.length - 1; end > 0; end -= 1) {
    let swapped = false
    for (let index = 0; index < end; index += 1) {
      steps.push(arrayStep({
        line: 4,
        type: 'compare',
        data: [...values],
        activeIndices: [index, index + 1],
        sortedIndices: [...sortedIndices],
        description: `比较 ${values[index]} 和 ${values[index + 1]}。`,
      }))
      if (values[index] > values[index + 1]) {
        ;[values[index], values[index + 1]] = [values[index + 1], values[index]]
        swapped = true
        steps.push(arrayStep({
          line: 5,
          type: 'swap',
          data: [...values],
          activeIndices: [index, index + 1],
          sortedIndices: [...sortedIndices],
          description: `左侧元素更大，交换为 ${values[index]}、${values[index + 1]}。`,
        }))
      }
    }
    sortedIndices.unshift(end)
    if (!swapped) break
  }

  steps.push(arrayStep({ line: 9, type: 'complete', data: [...values], sortedIndices: values.map((_, index) => index), description: '数组已经按升序排列完成。' }))
  return steps
}

function selectionSort(input: number[]): AlgorithmStep[] {
  const values = [...input]
  const steps: AlgorithmStep[] = [arrayStep({ line: 1, type: 'start', data: [...values], description: '从未排序区间寻找最小值。' })]
  const sortedIndices: number[] = []

  for (let start = 0; start < values.length - 1; start += 1) {
    let minimum = start
    for (let index = start + 1; index < values.length; index += 1) {
      steps.push(arrayStep({
        line: 5,
        type: 'compare',
        data: [...values],
        activeIndices: [minimum, index],
        sortedIndices: [...sortedIndices],
        description: `比较候选最小值 ${values[minimum]} 与 ${values[index]}。`,
      }))
      if (values[index] < values[minimum]) minimum = index
    }
    if (minimum !== start) {
      ;[values[start], values[minimum]] = [values[minimum], values[start]]
      steps.push(arrayStep({
        line: 8,
        type: 'swap',
        data: [...values],
        activeIndices: [start, minimum],
        sortedIndices: [...sortedIndices],
        description: `把最小值 ${values[start]} 放到未排序区间的起点。`,
      }))
    }
    sortedIndices.push(start)
  }

  sortedIndices.push(values.length - 1)
  steps.push(arrayStep({ line: 10, type: 'complete', data: [...values], sortedIndices, description: '每轮确定一个位置，排序完成。' }))
  return steps
}

function insertionSort(input: number[]): AlgorithmStep[] {
  const values = [...input]
  const steps: AlgorithmStep[] = [arrayStep({ line: 1, type: 'start', data: [...values], description: '把第一个元素视为已经排序的区间。' })]

  for (let index = 1; index < values.length; index += 1) {
    const current = values[index]
    let position = index - 1
    steps.push(arrayStep({ line: 4, type: 'compare', data: [...values], activeIndices: [index, position], description: `取出 ${current}，向左寻找插入位置。` }))
    while (position >= 0 && values[position] > current) {
      values[position + 1] = values[position]
      steps.push(arrayStep({ line: 6, type: 'move', data: [...values], activeIndices: [position, position + 1], description: `把 ${values[position]} 向右移动一格。` }))
      position -= 1
    }
    values[position + 1] = current
    steps.push(arrayStep({ line: 8, type: 'swap', data: [...values], activeIndices: [position + 1], description: `将 ${current} 插入到位置 ${position + 1}。` }))
  }

  steps.push(arrayStep({ line: 10, type: 'complete', data: [...values], sortedIndices: values.map((_, index) => index), description: '已排序区间扩展到整个数组。' }))
  return steps
}

function quickSort(input: number[]): AlgorithmStep[] {
  const values = [...input]
  const steps: AlgorithmStep[] = [arrayStep({ line: 1, type: 'start', data: [...values], description: '选择区间最右侧元素作为 pivot。' })]
  const sortedIndices: number[] = []

  function partition(left: number, right: number) {
    const pivot = values[right]
    let store = left
    for (let index = left; index < right; index += 1) {
      steps.push(arrayStep({ line: 5, type: 'compare', data: [...values], activeIndices: [index, right], sortedIndices: [...sortedIndices], description: `比较 ${values[index]} 与 pivot ${pivot}。` }))
      if (values[index] < pivot) {
        ;[values[store], values[index]] = [values[index], values[store]]
        steps.push(arrayStep({ line: 7, type: 'swap', data: [...values], activeIndices: [store, index], sortedIndices: [...sortedIndices], description: `将较小元素放入 pivot 左侧。` }))
        store += 1
      }
    }
    ;[values[store], values[right]] = [values[right], values[store]]
    sortedIndices.push(store)
    steps.push(arrayStep({ line: 9, type: 'swap', data: [...values], activeIndices: [store, right], sortedIndices: [...sortedIndices], description: `pivot 归位到索引 ${store}。` }))
    return store
  }

  function sort(left: number, right: number) {
    if (left >= right) {
      if (left === right && !sortedIndices.includes(left)) sortedIndices.push(left)
      return
    }
    const pivotIndex = partition(left, right)
    sort(left, pivotIndex - 1)
    sort(pivotIndex + 1, right)
  }

  sort(0, values.length - 1)
  steps.push(arrayStep({ line: 14, type: 'complete', data: [...values], sortedIndices: values.map((_, index) => index), description: '所有 pivot 都已归位，快速排序完成。' }))
  return steps
}

function binarySearch(input: number[], target = 9): AlgorithmStep[] {
  const values = [...input].sort((left, right) => left - right)
  const steps: AlgorithmStep[] = [arrayStep({ line: 1, type: 'start', data: [...values], target, description: `在有序数组中查找目标 ${target}。` })]
  let left = 0
  let right = values.length - 1

  while (left <= right) {
    const mid = left + Math.floor((right - left) / 2)
    steps.push(arrayStep({ line: 4, type: 'compare', data: [...values], activeIndices: [mid], left, right, mid, target, description: `取中点 ${mid}，当前值为 ${values[mid]}。` }))
    if (values[mid] === target) {
      steps.push(arrayStep({ line: 6, type: 'complete', data: [...values], activeIndices: [mid], left, right, mid, target, foundIndex: mid, description: `找到目标 ${target}，索引为 ${mid}。` }))
      return steps
    }
    if (values[mid] < target) {
      left = mid + 1
      steps.push(arrayStep({ line: 9, type: 'move', data: [...values], activeIndices: [mid], left, right, mid, target, description: `中点值偏小，搜索范围移动到右半段。` }))
    } else {
      right = mid - 1
      steps.push(arrayStep({ line: 11, type: 'move', data: [...values], activeIndices: [mid], left, right, mid, target, description: `中点值偏大，搜索范围收缩到左半段。` }))
    }
  }

  steps.push(arrayStep({ line: 14, type: 'complete', data: [...values], left, right, target, description: `搜索范围为空，没有找到 ${target}。` }))
  return steps
}

function createLinkedList(values: number[]): LinkedListNode[] {
  return values.map((value, index) => ({ id: `node-${index}`, value, nextId: values[index + 1] === undefined ? null : `node-${index + 1}` }))
}

function reverseLinkedList(input: number[]): AlgorithmStep[] {
  const nodes = createLinkedList(input)
  const steps: AlgorithmStep[] = [{
    kind: 'linked-list',
    line: 1,
    type: 'start',
    nodes: nodes.map(node => ({ ...node })),
    headId: nodes[0]?.id ?? null,
    currentId: nodes[0]?.id ?? null,
    previousId: null,
    nextId: nodes[0]?.nextId ?? null,
    description: '准备三个指针：previous、current 和 next。',
  }]
  let headId = nodes[0]?.id ?? null
  let previousId: string | null = null
  let currentId: string | null = headId

  while (currentId) {
    const current = nodes.find(node => node.id === currentId)
    if (!current) break
    const nextId = current.nextId
    steps.push({
      kind: 'linked-list', line: 4, type: 'visit', nodes: nodes.map(node => ({ ...node })), headId, currentId, previousId, nextId,
      description: `current 指向 ${current.value}，先保存它原来的 next。`,
    })
    current.nextId = previousId
    steps.push({
      kind: 'linked-list', line: 5, type: 'move', nodes: nodes.map(node => ({ ...node })), headId, currentId, previousId, nextId,
      description: `${current.value}.next 改为 previous，链表方向完成一次反转。`,
    })
    previousId = currentId
    currentId = nextId
    headId = previousId
    steps.push({
      kind: 'linked-list', line: 6, type: 'move', nodes: nodes.map(node => ({ ...node })), headId, currentId, previousId, nextId: currentId ? nodes.find(node => node.id === currentId)?.nextId ?? null : null,
      description: 'previous 和 current 同时向前推进。',
    })
  }

  steps.push({
    kind: 'linked-list', line: 8, type: 'complete', nodes: nodes.map(node => ({ ...node })), headId: previousId, currentId: null, previousId, nextId: null,
    description: 'current 为空，新的 head 就是 previous。',
  })
  return steps
}

function createGraphStep(input: Omit<GraphAlgorithmStep, 'kind' | 'nodes' | 'edges'>): GraphAlgorithmStep {
  return graphSnapshot({
    line: input.line,
    type: input.type,
    description: input.description,
    visited: [...input.visited],
    activeNode: input.activeNode,
    frontier: [...input.frontier],
    traversal: [...input.traversal],
  })
}

function breadthFirstSearch(): AlgorithmStep[] {
  const steps: AlgorithmStep[] = [createGraphStep({ line: 1, type: 'start', visited: [], activeNode: null, frontier: ['A'], traversal: [], description: '从 A 出发，把起点放入队列。' })]
  const visited: string[] = []
  const queue = ['A']

  while (queue.length > 0) {
    const current = queue.shift() as string
    if (visited.includes(current)) continue
    visited.push(current)
    steps.push(createGraphStep({ line: 4, type: 'visit', visited, activeNode: current, frontier: [...queue], traversal: [...visited], description: `出队 ${current}，标记为已访问。` }))
    const neighbors = graphEdges.filter(edge => edge.from === current).map(edge => edge.to)
    for (const neighbor of neighbors) {
      if (!visited.includes(neighbor) && !queue.includes(neighbor)) {
        queue.push(neighbor)
        steps.push(createGraphStep({ line: 7, type: 'enqueue', visited, activeNode: current, frontier: [...queue], traversal: [...visited], description: `把邻接节点 ${neighbor} 加入队列。` }))
      }
    }
  }

  steps.push(createGraphStep({ line: 11, type: 'complete', visited, activeNode: null, frontier: [], traversal: [...visited], description: `BFS 完成，访问顺序为 ${visited.join(' → ')}。` }))
  return steps
}

function depthFirstSearch(): AlgorithmStep[] {
  const steps: AlgorithmStep[] = [createGraphStep({ line: 1, type: 'start', visited: [], activeNode: null, frontier: ['A'], traversal: [], description: '从 A 开始递归探索。' })]
  const visited: string[] = []
  const stack: string[] = []

  function visit(current: string) {
    visited.push(current)
    stack.push(current)
    steps.push(createGraphStep({ line: 4, type: 'visit', visited, activeNode: current, frontier: [...stack], traversal: [...visited], description: `访问 ${current}，沿着一条路径继续深入。` }))
    const neighbors = graphEdges.filter(edge => edge.from === current).map(edge => edge.to)
    for (const neighbor of neighbors) {
      if (!visited.includes(neighbor)) {
        steps.push(createGraphStep({ line: 7, type: 'compare', visited, activeNode: current, frontier: [...stack, neighbor], traversal: [...visited], description: `检查 ${current} 的邻接节点 ${neighbor}。` }))
        visit(neighbor)
      }
    }
    stack.pop()
    steps.push(createGraphStep({ line: 10, type: 'move', visited, activeNode: stack.at(-1) ?? null, frontier: [...stack], traversal: [...visited], description: `${current} 的邻接节点处理完成，回溯到上一层。` }))
  }

  visit('A')
  steps.push(createGraphStep({ line: 13, type: 'complete', visited, activeNode: null, frontier: [], traversal: [...visited], description: `DFS 完成，访问顺序为 ${visited.join(' → ')}。` }))
  return steps
}

const sortCode = {
  bubble: ['function bubbleSort(nums) {', '  for (let end = nums.length - 1; end > 0; end--) {', '    for (let i = 0; i < end; i++) {', '      if (nums[i] > nums[i + 1]) {', '        [nums[i], nums[i + 1]] = [nums[i + 1], nums[i]];', '      }', '    }', '  }', '  return nums;', '}'],
  selection: ['function selectionSort(nums) {', '  for (let start = 0; start < nums.length - 1; start++) {', '    let minimum = start;', '    for (let i = start + 1; i < nums.length; i++) {', '      if (nums[i] < nums[minimum]) minimum = i;', '    }', '    [nums[start], nums[minimum]] = [nums[minimum], nums[start]];', '  }', '  return nums;', '}'],
  insertion: ['function insertionSort(nums) {', '  for (let i = 1; i < nums.length; i++) {', '    const current = nums[i];', '    let j = i - 1;', '    while (j >= 0 && nums[j] > current) {', '      nums[j + 1] = nums[j];', '      j--;', '    }', '    nums[j + 1] = current;', '  }', '  return nums;', '}'],
  quick: ['function quickSort(nums, left, right) {', '  if (left >= right) return;', '  const pivot = nums[right];', '  let store = left;', '  for (let i = left; i < right; i++) {', '    if (nums[i] < pivot) {', '      swap(nums, store, i);', '      store++;', '    }', '  }', '  swap(nums, store, right);', '  quickSort(nums, left, store - 1);', '  quickSort(nums, store + 1, right);', '}'],
  binary: ['function binarySearch(nums, target) {', '  let left = 0;', '  let right = nums.length - 1;', '  while (left <= right) {', '    const mid = left + (right - left) / 2;', '    if (nums[mid] === target) return mid;', '    if (nums[mid] < target) left = mid + 1;', '    else right = mid - 1;', '  }', '  return -1;', '}'],
  linkedList: ['function reverseList(head) {', '  let previous = null;', '  let current = head;', '  while (current !== null) {', '    const next = current.next;', '    current.next = previous;', '    previous = current;', '    current = next;', '  }', '  return previous;', '}'],
  bfs: ['function bfs(graph, start) {', '  const queue = [start];', '  const visited = new Set();', '  while (queue.length > 0) {', '    const current = queue.shift();', '    visited.add(current);', '    for (const next of graph[current]) {', '      if (!visited.has(next)) queue.push(next);', '    }', '  }', '}'],
  dfs: ['function dfs(graph, current, visited) {', '  visited.add(current);', '  for (const next of graph[current]) {', '    if (!visited.has(next)) {', '      dfs(graph, next, visited);', '    }', '  }', '}'],
}

export const visualizerAlgorithms: VisualizerAlgorithm[] = [
  { slug: 'bubble-sort', title: '冒泡排序', englishTitle: 'Bubble Sort', category: '数组与排序', kind: 'array', difficulty: '入门', timeComplexity: 'O(n²)', spaceComplexity: 'O(1)', description: '观察相邻元素不断比较和交换，较大的元素像气泡一样逐步浮到数组末端。', code: sortCode.bubble, defaultInput: [5, 3, 8, 2, 7, 4], buildSteps: bubbleSort },
  { slug: 'selection-sort', title: '选择排序', englishTitle: 'Selection Sort', category: '数组与排序', kind: 'array', difficulty: '入门', timeComplexity: 'O(n²)', spaceComplexity: 'O(1)', description: '每轮从未排序区间中选出最小值，放到当前应该出现的位置。', code: sortCode.selection, defaultInput: [7, 2, 9, 4, 1, 6], buildSteps: selectionSort },
  { slug: 'insertion-sort', title: '插入排序', englishTitle: 'Insertion Sort', category: '数组与排序', kind: 'array', difficulty: '入门', timeComplexity: 'O(n²)', spaceComplexity: 'O(1)', description: '像整理扑克牌一样，把当前元素插入左侧已经排好序的区间。', code: sortCode.insertion, defaultInput: [6, 3, 8, 1, 5, 2], buildSteps: insertionSort },
  { slug: 'quick-sort', title: '快速排序', englishTitle: 'Quick Sort', category: '数组与排序', kind: 'array', difficulty: '基础', timeComplexity: 'O(n log n)', spaceComplexity: 'O(log n)', description: '围绕 pivot 划分数组，让较小元素留在左侧，再递归处理两个子区间。', code: sortCode.quick, defaultInput: [8, 3, 6, 2, 7, 4, 5], buildSteps: quickSort },
  { slug: 'binary-search', title: '二分查找', englishTitle: 'Binary Search', category: '查找算法', kind: 'array', difficulty: '基础', timeComplexity: 'O(log n)', spaceComplexity: 'O(1)', description: '每次检查中点并排除一半不可能的区间，在有序数组中快速定位目标。', code: sortCode.binary, defaultInput: [2, 5, 8, 12, 16, 23, 38, 56, 72], defaultTarget: 23, buildSteps: binarySearch },
  { slug: 'reverse-linked-list', title: '链表反转', englishTitle: 'Reverse Linked List', category: '数据结构', kind: 'linked-list', difficulty: '基础', timeComplexity: 'O(n)', spaceComplexity: 'O(1)', description: '同步观察 previous、current、next 三个指针，理解每一次 next 指向反转。', code: sortCode.linkedList, defaultInput: [10, 20, 30, 40], buildSteps: reverseLinkedList },
  { slug: 'bfs', title: '广度优先搜索', englishTitle: 'Breadth-First Search', category: '图算法', kind: 'graph', difficulty: '基础', timeComplexity: 'O(V + E)', spaceComplexity: 'O(V)', description: '用队列逐层展开图，清晰看到已访问节点、当前节点和待处理队列。', code: sortCode.bfs, defaultInput: [], buildSteps: () => breadthFirstSearch() },
  { slug: 'dfs', title: '深度优先搜索', englishTitle: 'Depth-First Search', category: '图算法', kind: 'graph', difficulty: '基础', timeComplexity: 'O(V + E)', spaceComplexity: 'O(V)', description: '沿一条路径不断深入，再通过回溯返回上一层继续探索。', code: sortCode.dfs, defaultInput: [], buildSteps: () => depthFirstSearch() },
]

export function getVisualizerAlgorithm(slug?: string) {
  return visualizerAlgorithms.find(algorithm => algorithm.slug === slug)
}

export function parseNumberInput(value: string, fallback: number[]) {
  const numbers = value.split(/[,\s，]+/).map(item => Number(item)).filter(item => Number.isFinite(item))
  return numbers.length > 0 ? numbers.slice(0, 12) : fallback
}

export function formatNumberInput(values: number[]) {
  return values.join(', ')
}
