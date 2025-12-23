"""
游戏策略基类 - 定义AI决策逻辑
Base Strategy - Define AI Decision Logic
"""

from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional
from enum import Enum
import time

from ..detector.yolo_detector import Detection


class GameState(Enum):
    """游戏状态枚举"""
    IDLE = "idle"              # 空闲
    MENU = "menu"              # 菜单界面
    BATTLE = "battle"          # 战斗中
    LOADING = "loading"        # 加载中
    REWARD = "reward"          # 领取奖励
    DIALOGUE = "dialogue"      # 对话界面
    UNKNOWN = "unknown"        # 未知状态


class BaseStrategy(ABC):
    """游戏策略基类"""

    def __init__(self, name: str = "BaseStrategy"):
        """
        Args:
            name: 策略名称
        """
        self.name = name
        self.current_state = GameState.UNKNOWN
        self.frame_count = 0
        self.last_action_time = 0
        self.action_cooldown = 0.5  # 操作冷却时间(秒)

    @abstractmethod
    def analyze_state(self, frame, detections: List[Detection]) -> GameState:
        """
        分析当前游戏状态

        Args:
            frame: 当前帧图像
            detections: 检测结果

        Returns:
            游戏状态
        """
        pass

    @abstractmethod
    def make_decision(
        self,
        frame,
        detections: List[Detection],
        state: GameState
    ) -> Dict[str, Any]:
        """
        做出决策

        Args:
            frame: 当前帧图像
            detections: 检测结果
            state: 当前游戏状态

        Returns:
            决策结果字典 {"action": str, "params": dict}
        """
        pass

    def update(self, frame, detections: List[Detection]) -> Optional[Dict[str, Any]]:
        """
        更新策略 - 主循环调用

        Args:
            frame: 当前帧图像
            detections: 检测结果

        Returns:
            决策结果或None
        """
        self.frame_count += 1

        # 分析状态
        self.current_state = self.analyze_state(frame, detections)

        # 检查操作冷却
        current_time = time.time()
        if current_time - self.last_action_time < self.action_cooldown:
            return None

        # 做出决策
        decision = self.make_decision(frame, detections, self.current_state)

        if decision:
            self.last_action_time = current_time

        return decision

    def get_detections_by_class(
        self,
        detections: List[Detection],
        class_name: str
    ) -> List[Detection]:
        """获取指定类别的检测结果"""
        return [det for det in detections if det.class_name == class_name]

    def has_detection(self, detections: List[Detection], class_name: str) -> bool:
        """检查是否存在某类检测目标"""
        return any(det.class_name == class_name for det in detections)


class SimpleStrategy(BaseStrategy):
    """简单策略示例 - 可作为模板"""

    def __init__(self):
        super().__init__(name="SimpleStrategy")

        # 定义状态对应的UI元素
        self.state_indicators = {
            GameState.MENU: ["start_button", "menu_bg"],
            GameState.BATTLE: ["enemy", "hp_bar", "skill_button"],
            GameState.REWARD: ["reward_icon", "claim_button"],
            GameState.LOADING: ["loading_icon"],
        }

    def analyze_state(self, frame, detections: List[Detection]) -> GameState:
        """分析游戏状态"""

        # 根据检测到的UI元素判断状态
        for state, indicators in self.state_indicators.items():
            for indicator in indicators:
                if self.has_detection(detections, indicator):
                    return state

        return GameState.UNKNOWN

    def make_decision(
        self,
        frame,
        detections: List[Detection],
        state: GameState
    ) -> Dict[str, Any]:
        """做出决策"""

        if state == GameState.MENU:
            return self._handle_menu(detections)

        elif state == GameState.BATTLE:
            return self._handle_battle(detections)

        elif state == GameState.REWARD:
            return self._handle_reward(detections)

        elif state == GameState.LOADING:
            return {"action": "wait", "params": {}}

        else:
            return {"action": "wait", "params": {}}

    def _handle_menu(self, detections: List[Detection]) -> Dict[str, Any]:
        """处理菜单界面"""
        # 查找开始按钮
        start_buttons = self.get_detections_by_class(detections, "start_button")

        if start_buttons:
            button = start_buttons[0]
            cx, cy = button.center
            return {
                "action": "tap",
                "params": {"x": cx, "y": cy}
            }

        return {"action": "wait", "params": {}}

    def _handle_battle(self, detections: List[Detection]) -> Dict[str, Any]:
        """处理战斗界面"""
        # 查找敌人
        enemies = self.get_detections_by_class(detections, "enemy")

        if enemies:
            # 攻击最近的敌人
            enemy = enemies[0]
            cx, cy = enemy.center
            return {
                "action": "tap",
                "params": {"x": cx, "y": cy}
            }

        # 查找技能按钮
        skills = self.get_detections_by_class(detections, "skill_button")
        if skills:
            skill = skills[0]
            cx, cy = skill.center
            return {
                "action": "tap",
                "params": {"x": cx, "y": cy}
            }

        return {"action": "wait", "params": {}}

    def _handle_reward(self, detections: List[Detection]) -> Dict[str, Any]:
        """处理奖励界面"""
        # 查找领取按钮
        claim_buttons = self.get_detections_by_class(detections, "claim_button")

        if claim_buttons:
            button = claim_buttons[0]
            cx, cy = button.center
            return {
                "action": "tap",
                "params": {"x": cx, "y": cy}
            }

        return {"action": "wait", "params": {}}


class StateMachineStrategy(BaseStrategy):
    """状态机策略 - 更复杂的决策逻辑"""

    def __init__(self):
        super().__init__(name="StateMachineStrategy")

        # 状态转换表
        self.transitions = {
            GameState.MENU: [GameState.LOADING, GameState.BATTLE],
            GameState.LOADING: [GameState.BATTLE, GameState.MENU],
            GameState.BATTLE: [GameState.REWARD, GameState.LOADING],
            GameState.REWARD: [GameState.MENU, GameState.BATTLE],
        }

        # 状态历史
        self.state_history = []
        self.max_history = 10

    def analyze_state(self, frame, detections: List[Detection]) -> GameState:
        """分析游戏状态"""
        # TODO: 实现更复杂的状态识别逻辑
        return GameState.UNKNOWN

    def make_decision(
        self,
        frame,
        detections: List[Detection],
        state: GameState
    ) -> Dict[str, Any]:
        """做出决策"""
        # 记录状态历史
        self._update_state_history(state)

        # TODO: 基于状态机的决策逻辑
        return {"action": "wait", "params": {}}

    def _update_state_history(self, state: GameState):
        """更新状态历史"""
        self.state_history.append(state)
        if len(self.state_history) > self.max_history:
            self.state_history.pop(0)

    def _can_transition(self, from_state: GameState, to_state: GameState) -> bool:
        """检查状态转换是否合法"""
        if from_state not in self.transitions:
            return True
        return to_state in self.transitions[from_state]


# 测试代码
if __name__ == "__main__":
    print("=== 测试游戏策略 ===\n")

    strategy = SimpleStrategy()

    # 模拟检测结果
    from ..detector.yolo_detector import Detection

    mock_detections = [
        Detection(0, "start_button", 0.95, (100, 200, 300, 400), (200, 300)),
        Detection(1, "menu_bg", 0.98, (0, 0, 720, 1280), (360, 640)),
    ]

    # 分析状态
    state = strategy.analyze_state(None, mock_detections)
    print(f"✓ 检测到状态: {state.value}")

    # 做出决策
    decision = strategy.make_decision(None, mock_detections, state)
    print(f"✓ 决策结果: {decision}")
