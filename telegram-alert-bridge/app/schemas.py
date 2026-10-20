from typing import Optional
from pydantic import BaseModel, Field


class AlertItem(BaseModel):
    status: str = Field(
        default="firing",
        description="alert status"
    )
    labels: dict[str, str] = Field(
        default_factory=dict,
        description="prometheus labels"
    )
    annotations: dict[str, str] = Field(
        default_factory=dict,
        description="alert annotations"
    )
    startsAt: Optional[str] = Field(
        default=None,
        description="start timestamp"
    )
    endsAt: Optional[str] = Field(
        default=None,
        description="end timestamp"
    )
    generatorURL: Optional[str] = Field(
        default=None,
        description="prometheus graph url"
    )
    fingerprint: Optional[str] = Field(
        default=None,
        description="alert fingerprint"
    )


class AlertmanagerWebhook(BaseModel):
    version: str = Field(
        default="4",
        description="webhook version"
    )
    groupKey: Optional[str] = Field(
        default=None,
        description="group key"
    )
    status: str = Field(
        default="firing",
        description="group alert status"
    )
    receiver: Optional[str] = Field(
        default=None,
        description="receiver name"
    )
    groupLabels: dict[str, str] = Field(default_factory=dict)
    commonLabels: dict[str, str] = Field(default_factory=dict)
    commonAnnotations: dict[str, str] = Field(default_factory=dict)
    externalURL: Optional[str] = Field(
        default=None,
        description="alertmanager external url"
    )
    alerts: list[AlertItem] = Field(
        default_factory=list,
        description="list of alerts"
    )
